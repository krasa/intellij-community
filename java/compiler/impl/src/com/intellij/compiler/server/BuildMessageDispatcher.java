/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compiler.server;

import com.intellij.openapi.diagnostic.Logger;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.io.SimpleChannelInboundHandlerAdapter;
import org.jetbrains.jps.api.CmdlineProtoUtil;
import org.jetbrains.jps.api.CmdlineRemoteProto;

import java.util.UUID;

/**
 * @author Eugene Zhuravlev
 *         Date: 4/25/12
 */
@ChannelHandler.Sharable
class BuildMessageDispatcher extends SimpleChannelInboundHandlerAdapter<CmdlineRemoteProto.Message> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.server.BuildMessageDispatcher");
  protected static final AttributeKey<BuildSession> SESSION_DATA = AttributeKey.valueOf("BuildMessageDispatcher.sessionData");

  private final BuildSessionPool myBuildSessionPool;

  public BuildMessageDispatcher(@NotNull BuildSessionPool buildSessionPool) {
    myBuildSessionPool = buildSessionPool;
  }

  @Override
  protected void messageReceived(ChannelHandlerContext context, CmdlineRemoteProto.Message message) throws Exception {
    BuildSession buildSession = context.attr(SESSION_DATA).get();

    UUID sessionId;
    if (buildSession == null) {
      // this is the first message for this session, so fill session data with missing info
      final CmdlineRemoteProto.Message.UUID id = message.getSessionId();
      sessionId = new UUID(id.getMostSigBits(), id.getLeastSigBits());

      buildSession = myBuildSessionPool.getSession(sessionId);
      if (buildSession != null) {
        buildSession.myChannel = context.channel();
        context.attr(SESSION_DATA).set(buildSession);
      }
      if (myBuildSessionPool.isCancelled(sessionId)) {
        context.channel().writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createCancelCommand()));
      }
    }
    else {
      sessionId = buildSession.sessionId;
    }

    final BuilderMessageHandler handler = buildSession != null ? buildSession.handler : null;
    if (handler == null) {
      // todo
      LOG.info("No message handler registered for session " + sessionId);
      return;
    }

    final CmdlineRemoteProto.Message.Type messageType = message.getType();
    switch (messageType) {
      case FAILURE:
        handler.handleFailure(sessionId, message.getFailure());
        buildSession.setState(BuildSessionState.IDLE);
        handler.buildFinished(sessionId);
        break;

      case BUILDER_MESSAGE:
        final CmdlineRemoteProto.Message.BuilderMessage builderMessage = message.getBuilderMessage();
        final CmdlineRemoteProto.Message.BuilderMessage.Type msgType = builderMessage.getType();
        if (msgType == CmdlineRemoteProto.Message.BuilderMessage.Type.PARAM_REQUEST) {
          final CmdlineRemoteProto.Message.ControllerMessage params = buildSession.params;
          if (params != null) {
            handler.buildStarted(sessionId);
            buildSession.params = null;
            context.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, params));
          }
          else {
            myBuildSessionPool.cancelSession(sessionId);
          }
        }
        else {
          handler.handleBuildMessage(context.channel(), sessionId, builderMessage);
          if (builderMessage.getType() == CmdlineRemoteProto.Message.BuilderMessage.Type.BUILD_EVENT) {
            final CmdlineRemoteProto.Message.BuilderMessage.BuildEvent event = builderMessage.getBuildEvent();
            switch (event.getEventType()) {
              case BUILD_COMPLETED: {
                buildSession.setState(BuildSessionState.IDLE);
                handler.buildFinished(sessionId);
              }
            }
          }
        }
        break;

      default:
        LOG.info("Unsupported message type " + messageType);
        break;
    }
  }

  @Override
  public void channelInactive(ChannelHandlerContext context) throws Exception {
    try {
      super.channelInactive(context);
    }
    finally {
      final BuildSession buildSession = context.attr(SESSION_DATA).get();
      if (buildSession != null) {
        final BuilderMessageHandler handler = myBuildSessionPool.destroySession(buildSession.sessionId);
        if (handler != null) {
          // notify the handler only if it has not been notified yet
          handler.buildFinished(buildSession.sessionId);
        }
      }
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext context, Throwable cause) throws Exception {
    if (cause != null) {
      LOG.info(cause);
    }
  }

}
