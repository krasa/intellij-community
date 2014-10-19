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

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ConcurrentHashSet;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.SimpleChannelInboundHandlerAdapter;
import org.jetbrains.jps.api.CmdlineProtoUtil;
import org.jetbrains.jps.api.CmdlineRemoteProto;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Eugene Zhuravlev
 *         Date: 4/25/12
 */
@ChannelHandler.Sharable
class BuildMessageDispatcher extends SimpleChannelInboundHandlerAdapter<CmdlineRemoteProto.Message> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.server.BuildMessageDispatcher");

  private static final AttributeKey<SessionData> SESSION_DATA = AttributeKey.valueOf("BuildMessageDispatcher.sessionData");
  public static final int MAX_IDLE_CHANNELS = 3;

  private final Map<UUID, SessionData> myMessageHandlers = new ConcurrentHashMap<UUID, SessionData>(16, 0.75f, 1);
  private final Set<UUID> myCanceledSessions = new ConcurrentHashSet<UUID>();

  public SessionData registerBuildMessageHandler(Project project,
                                                 UUID sessionId,
                                                 BuilderMessageHandler handler,
                                                 CmdlineRemoteProto.Message.ControllerMessage params,
                                                 GeneralCommandLine commandLine) {
    SessionData value = new SessionData(sessionId, handler, params, project.getProjectFilePath(), commandLine);
    tryReusingChannel(value);
    myMessageHandlers.put(sessionId, value);
    closeOldChannels(value);
    return value;
  }

  @Nullable
  public SessionData getSession(UUID sessionId) {
    return myMessageHandlers.get(sessionId);
  }
  
  @Nullable
  public SessionData getWorkingSession(Project project) {
    for (SessionData sessionData : myMessageHandlers.values()) {
      if (sessionData.isWorking() && sessionData.myProjectFilePath.equals(project.getProjectFilePath())) {
        return sessionData;
      }
    }
    return null;
  }


  private void tryReusingChannel(@NotNull SessionData newSession) {
    SessionData oldSession = getPreviousSessionByProject(newSession);
    if (oldSession != null && oldSession.state == ProcessState.IDLE) {
      LOG.info("Found channel from sessionId=" + oldSession.sessionId);
      if (oldSession.isCommandLineChanged(newSession)) {
        LOG.info("Command line changed");
        LOG.info("oldCmd: " + oldSession.myProcessCommandLine);
        LOG.info("newCmd: " + newSession.myProcessCommandLine);
        closeChannel(oldSession);
      }
      else {
        LOG.info("Reusing channel");
        newSession.channel = oldSession.channel;
        newSession.setState(ProcessState.WORKING);
        oldSession.channel.attr(SESSION_DATA).set(newSession);
        myMessageHandlers.remove(oldSession.sessionId);
      }
    }
    else if (oldSession != null) {
      String s = "cannot reuse channel for sessionId=" +
                 newSession.sessionId +
                 "existing session: state=" +
                 oldSession.state +
                 " sessionId=" +
                 oldSession.sessionId;
      throw new IllegalStateException(s);
    }
    else {
      LOG.info("no active channel, sessionId=" + newSession.sessionId);
    }
  }

  @Nullable
  private SessionData getPreviousSessionByProject(@NotNull SessionData project) {
    String projectFilePath = project.myProjectFilePath;
    SessionData result = null;
    for (SessionData sessionData : myMessageHandlers.values()) {
      if (sessionData.myProjectFilePath.equals(projectFilePath)) {
        if (result != null) {
          throw new IllegalStateException("More than one session for project, state:" + result.state + "," + sessionData.state);
        }
        result = sessionData;
      }
    }
    return result;
  }

  private void closeOldChannels(@NotNull SessionData actualSession) {
    List<SessionData> idleSessions = new ArrayList<SessionData>();
    for (SessionData sessionData : myMessageHandlers.values()) {
      if (sessionData.state == ProcessState.IDLE && sessionData != actualSession) {
        idleSessions.add(sessionData);
      }
    }
    Collections.sort(idleSessions, new Comparator<SessionData>() {
      @Override
      public int compare(SessionData o1, SessionData o2) {
        if (o1.idleFrom == null || o2.idleFrom == null) return 0;
        return o2.idleFrom.compareTo(o1.idleFrom);
      }
    });
    for (int i = 0; i < idleSessions.size(); i++) {
      SessionData sessionData = idleSessions.get(i);
      if (i + 1 >= MAX_IDLE_CHANNELS) {
        closeChannel(sessionData);
      }
    }
  }

  public void projectClosed(@NotNull Project project) {
    String projectFilePath = project.getProjectFilePath();
    for (SessionData sessionData : myMessageHandlers.values()) {
      if (projectFilePath.equals(sessionData.myProjectFilePath)) {
        if (sessionData.state == ProcessState.IDLE) {
          closeChannel(sessionData);
        }
        else {
          throw new IllegalStateException("SessionData in strange state=" + sessionData.state + " for project " + projectFilePath);
        }
      }
    }
  }

  public void closeChannel(@NotNull SessionData sessionData) {
    LOG.info("Closing channel for sessionId=" + sessionData.sessionId + " project=" + sessionData.myProjectFilePath);
    sessionData.channel.close();
  }

  @Nullable
  public BuilderMessageHandler unregisterBuildMessageHandler(UUID sessionId) {
    myCanceledSessions.remove(sessionId);
    final SessionData data = myMessageHandlers.remove(sessionId);
    if (data != null) {
      LOG.info("session " + sessionId + " removed for " + data.myProjectFilePath);
      Channel channel = data.channel;
      if (channel != null && channel.isOpen()) {
        LOG.error("closing channel (it should never happen)");
        channel.close();
      }
    }
    return data != null ? data.handler : null;
  }

  public void cancelSession(UUID sessionId) {
    LOG.info("cancelSession, sessionId=" + sessionId);
    if (myCanceledSessions.add(sessionId)) {
      final Channel channel = getConnectedChannel(sessionId);
      if (channel != null) {
        LOG.info("sending cancel command");
        channel.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createCancelCommand()));
      }
      else {
        SessionData sessionData = myMessageHandlers.get(sessionId);
        if (sessionData != null) {
          BuilderMessageHandler handler = sessionData.handler;
          if (handler != null) {
            LOG.error("terminating session that had no channel");
            handler.sessionTerminated(sessionId);
          }
        }
      } 
    }
  }

  @Nullable
  public Channel getConnectedChannel(final UUID sessionId) {
    final Channel channel = getAssociatedChannel(sessionId);
    return channel != null && channel.isActive() ? channel : null;
  }

  @Nullable
  public Channel getAssociatedChannel(final UUID sessionId) {
    final SessionData data = myMessageHandlers.get(sessionId);
    return data != null ? data.channel : null;
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
      }
      if (myCanceledSessions.contains(sessionId)) {
        context.channel().writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createCancelCommand()));
      }
    }
    else {
      sessionId = sessionData.sessionId;
    }

    final BuilderMessageHandler handler = sessionData != null ? sessionData.handler : null;
    if (handler == null) {
      // todo
      LOG.info("No message handler registered for session " + sessionId);
      return;
    }

    final CmdlineRemoteProto.Message.Type messageType = message.getType();
    switch (messageType) {
      case FAILURE:
        handler.handleFailure(sessionId, message.getFailure());
        sessionData.setState(ProcessState.IDLE);
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
          if (builderMessage.getType() == CmdlineRemoteProto.Message.BuilderMessage.Type.BUILD_EVENT) {
            final CmdlineRemoteProto.Message.BuilderMessage.BuildEvent event = builderMessage.getBuildEvent();
            switch (event.getEventType()) {
              case BUILD_COMPLETED: {
                sessionData.setState(ProcessState.IDLE);
              }
            }
          }
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
          //show error to the user only if the process died during the build
          if (sessionData.isWorking()) {
            handler.handleFailure(sessionData.sessionId, CmdlineProtoUtil.createFailure("Build process was terminated", null));
          }
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

  static final class SessionData {
    private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.server.BuildMessageDispatcher.SessionData");

    final UUID sessionId;
    final BuilderMessageHandler handler;
    final String myProjectFilePath;
    final String myProcessCommandLine;
    volatile CmdlineRemoteProto.Message.ControllerMessage params;
    volatile Channel channel;
    volatile ProcessState state = ProcessState.INIT;
    volatile Date idleFrom;

    private SessionData(UUID sessionId,
                        BuilderMessageHandler handler,
                        CmdlineRemoteProto.Message.ControllerMessage params,
                        String projectFilePath,
                        GeneralCommandLine processCommandLine) {
      this.sessionId = sessionId;
      this.handler = handler;
      this.params = params;
      myProjectFilePath = projectFilePath;
      myProcessCommandLine = processCommandLine.getCommandLineString().replace(sessionId.toString(), "#sessionId");
    }

    public void setState(ProcessState state) {
      LOG.info("updating state to " + state + ", sessionId=" + sessionId);
      this.state = state;
      if (state == ProcessState.IDLE) {
        idleFrom = new Date();
      }
    }

    public ProcessState getState() {
      return state;
    }

    public boolean isWorking() {
      return state == ProcessState.WORKING;
    }

    public boolean isCommandLineChanged(SessionData newSession) {
      return !this.myProcessCommandLine.equals(newSession.myProcessCommandLine);
    }
  }

  enum ProcessState {
    INIT, IDLE, WORKING
  }
}
