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
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ConcurrentHashSet;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.SimpleChannelInboundHandlerAdapter;
import org.jetbrains.jps.api.CmdlineProtoUtil;
import org.jetbrains.jps.api.CmdlineRemoteProto;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
* @author Eugene Zhuravlev
*         Date: 4/25/12
*/
@ChannelHandler.Sharable
class BuildMessageDispatcher extends SimpleChannelInboundHandlerAdapter<CmdlineRemoteProto.Message> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.server.BuildMessageDispatcher");

  private static final AttributeKey<SessionData> SESSION_DATA = AttributeKey.valueOf("BuildMessageDispatcher.sessionData");

  private final Map<UUID, SessionData> myMessageHandlers = new ConcurrentHashMap<UUID, SessionData>(16, 0.75f, 1);
  private final Map<String, Channel> myProjectPathToChannel = new ConcurrentHashMap<String, Channel>(16, 0.75f, 1);
  private final Set<UUID> myCanceledSessions = new ConcurrentHashSet<UUID>();

  public void registerBuildMessageHandler(Project project, UUID sessionId,
                                          BuilderMessageHandler handler,
                                          CmdlineRemoteProto.Message.ControllerMessage params) {
    SessionData value = new SessionData(sessionId, handler, params, project.getProjectFilePath());
    myMessageHandlers.put(sessionId, value);
    Channel channel = myProjectPathToChannel.get(project.getProjectFilePath());
    if (channel != null) {
      LOG.info("Reusing channel");
      value.channel = channel;
      channel.attr(SESSION_DATA).set(value);
    } 
  }
  
  @Nullable
  public BuilderMessageHandler unregisterBuildMessageHandler(UUID sessionId) {
    myCanceledSessions.remove(sessionId);
    final SessionData data = myMessageHandlers.remove(sessionId);
    return data != null? data.handler : null;
  }

  public void cancelSession(UUID sessionId) {
    if (myCanceledSessions.add(sessionId)) {
      final Channel channel = getConnectedChannel(sessionId);
      if (channel != null) {
        channel.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createCancelCommand()));
      }
    }
  }

  @Nullable
  public Channel getConnectedChannel(final UUID sessionId) {
    final Channel channel = getAssociatedChannel(sessionId);
    return channel != null && channel.isActive()? channel : null;
  }

  @Nullable
  public Channel getAssociatedChannel(final UUID sessionId) {
    final SessionData data = myMessageHandlers.get(sessionId);
    return data != null? data.channel : null;
  }

  public void remove(Project project) {
    myProjectPathToChannel.remove(project.getProjectFilePath());
  }

  @Nullable
  public Channel getAssociatedChannel(Project project) {
    return myProjectPathToChannel.get(project.getProjectFilePath());
  }

  @Override
  protected void messageReceived(ChannelHandlerContext context, CmdlineRemoteProto.Message message) throws Exception {
    SessionData sessionData = context.attr(SESSION_DATA).get();

    UUID sessionId;
    if (sessionData == null) {
      // this is the first message for this session, so fill session data with missing info
      final CmdlineRemoteProto.Message.UUID id = message.getSessionId();
      sessionId = new UUID(id.getMostSigBits(), id.getLeastSigBits());

      sessionData = myMessageHandlers.get(sessionId);
      if (sessionData != null) {
        sessionData.channel = context.channel();
        context.attr(SESSION_DATA).set(sessionData);
        myProjectPathToChannel.put(sessionData.myProjectFilePath, context.channel());
      }
      if (myCanceledSessions.contains(sessionId)) {
        context.channel().writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createCancelCommand()));
      }
    }
    else {
      sessionId = sessionData.sessionId;
    }

    final BuilderMessageHandler handler = sessionData != null? sessionData.handler : null;
    if (handler == null) {
      // todo
      LOG.info("No message handler registered for session " + sessionId);
      return;
    }

    final CmdlineRemoteProto.Message.Type messageType = message.getType();
    switch (messageType) {
      case FAILURE:
        handler.handleFailure(sessionId, message.getFailure());
        break;

      case BUILDER_MESSAGE:
        final CmdlineRemoteProto.Message.BuilderMessage builderMessage = message.getBuilderMessage();
        final CmdlineRemoteProto.Message.BuilderMessage.Type msgType = builderMessage.getType();
        if (msgType == CmdlineRemoteProto.Message.BuilderMessage.Type.PARAM_REQUEST) {
          final CmdlineRemoteProto.Message.ControllerMessage params = sessionData.params;
          if (params != null) {
            handler.buildStarted(sessionId);
            sessionData.params = null;
            context.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, params));
          }
          else {
            cancelSession(sessionId);
          }
        }
        else {
          handler.handleBuildMessage(context.channel(), sessionId, builderMessage);
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
      final SessionData sessionData = context.attr(SESSION_DATA).get();
      if (sessionData != null) {
        final BuilderMessageHandler handler = unregisterBuildMessageHandler(sessionData.sessionId);
        if (handler != null) {
          // notify the handler only if it has not been notified yet
          handler.sessionTerminated(sessionData.sessionId);
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

  private static final class SessionData {
    final UUID sessionId;
    final BuilderMessageHandler handler;
    final String myProjectFilePath;
    volatile CmdlineRemoteProto.Message.ControllerMessage params;
    volatile Channel channel;

    private SessionData(UUID sessionId,
                        BuilderMessageHandler handler,
                        CmdlineRemoteProto.Message.ControllerMessage params,
                        String projectFilePath) {
      this.sessionId = sessionId;
      this.handler = handler;
      this.params = params;
      myProjectFilePath = projectFilePath;
    }
  }
}
