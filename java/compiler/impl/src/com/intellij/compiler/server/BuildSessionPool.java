/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ConcurrentHashSet;
import io.netty.channel.Channel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.CmdlineProtoUtil;
import org.jetbrains.jps.api.CmdlineRemoteProto;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

class BuildSessionPool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.server.BuildSessionPool");
  protected static final Comparator<BuildSession> SESSION_DATA_COMPARATOR = new Comparator<BuildSession>() {
    @Override
    public int compare(BuildSession o1, BuildSession o2) {
      Date idleFrom = o1.idleFrom;
      Date idleFrom2 = o2.idleFrom;
      if (idleFrom == null && idleFrom2 == null) return 0;
      if (idleFrom == null) return 1;
      if (idleFrom2 == null) return -1;
      return idleFrom.compareTo(idleFrom2);
    }
  };

  private final Map<UUID, BuildSession> mySessions = new ConcurrentHashMap<UUID, BuildSession>(16, 0.75f, 1);
  private final Set<UUID> myCanceledSessions = new ConcurrentHashSet<UUID>();

  public BuildSession registerSession(Project project,
                                      UUID sessionId,
                                      BuilderMessageHandler handler,
                                      CmdlineRemoteProto.Message.ControllerMessage params,
                                      GeneralCommandLine commandLine) {
    BuildSession newSession = new BuildSession(sessionId, handler, params, project.getProjectFilePath(), commandLine);
    tryReusingChannel(newSession, project);
    mySessions.put(sessionId, newSession);
    closeOldChannels(newSession);
    return newSession;
  }

  @Nullable
  public BuildSession getSession(UUID sessionId) {
    return mySessions.get(sessionId);
  }

  @Nullable
  public BuildSession getWorkingSession(Project project) {
    BuildSession sessionByProject = getSessionByProject(project);
    if (sessionByProject != null && sessionByProject.state == BuildSessionState.WORKING) {
      return sessionByProject;
    }
    return null;
  }

  @Nullable
  public BuildSession getSessionByProject(@NotNull Project project) {
    String projectFilePath = project.getProjectFilePath();
    BuildSession result = null;
    for (BuildSession buildSession : mySessions.values()) {
      if (buildSession.myProjectFilePath.equals(projectFilePath)) {
        if (result != null) {
          throw new IllegalStateException("More than one session for project, state:" + result.state + "," + buildSession.state);
        }
        result = buildSession;
      }
    }
    return result;
  }


  private void tryReusingChannel(@NotNull BuildSession newSession, @NotNull Project project) {
    BuildSession oldSession = getSessionByProject(project);
    if (oldSession != null && oldSession.state == BuildSessionState.IDLE) {
      LOG.debug("Found channel from sessionId=" + oldSession.sessionId);
      if (oldSession.isCommandLineChanged(newSession)) {
        LOG.debug("Command line changed");
        LOG.debug("oldCmd: " + oldSession.myProcessCommandLine);
        LOG.debug("newCmd: " + newSession.myProcessCommandLine);
        oldSession.closeChannel();
        //no need to wait on event, also we do not want to have multiple sessions for the same project
        mySessions.remove(oldSession.sessionId);
      }
      else {
        LOG.debug("Reusing channel");
        newSession.myChannel = oldSession.myChannel;
        newSession.myChannel.attr(BuildMessageDispatcher.SESSION_DATA).set(newSession);
        newSession.setState(BuildSessionState.WORKING);
        mySessions.remove(oldSession.sessionId);
      }
    }
    else if (oldSession != null) {
      String s = "Cannot reuse channel for sessionId=" +
                 newSession.sessionId +
                 "existing session: state=" +
                 oldSession.state +
                 " sessionId=" +
                 oldSession.sessionId;
      throw new IllegalStateException(s);
    }
    else {
      LOG.debug("No active channel, sessionId=" + newSession.sessionId);
    }
  }

  private void closeOldChannels(@NotNull BuildSession actualSession) {
    List<BuildSession> idleSessions = new ArrayList<BuildSession>();
    for (BuildSession buildSession : mySessions.values()) {
      if (buildSession.state == BuildSessionState.IDLE && buildSession != actualSession) {
        idleSessions.add(buildSession);
      }
    }
    Collections.sort(idleSessions, SESSION_DATA_COMPARATOR);

    int limit = Registry.intValue("compiler.max.idle.processes");
    while (!idleSessions.isEmpty() && idleSessions.size() > limit) {
      idleSessions.get(0).closeChannel();
      idleSessions.remove(0);
    }
  }

  @Nullable
  public BuilderMessageHandler destroySession(@NotNull UUID sessionId) {
    myCanceledSessions.remove(sessionId);
    final BuildSession data = mySessions.remove(sessionId);
    if (data != null) {
      LOG.debug("session " + sessionId + " removed for " + data.myProjectFilePath);
      //close channel to prevent process leak;
      data.closeChannel();
    }
    return data != null ? data.handler : null;
  }

  public void cancelSession(UUID sessionId) {
    LOG.debug("Cancelling session, sessionId=" + sessionId);
    if (myCanceledSessions.add(sessionId)) {
      final Channel channel = getConnectedChannel(sessionId);
      if (channel != null ) {
        LOG.debug("Sending cancel command, sessionId=" + sessionId);
        channel.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, CmdlineProtoUtil.createCancelCommand()));
      }
      else {
        BuildSession session = getSession(sessionId);
        if (session != null && session.handler != null) {
          //prevent any deadlocks
          destroySession(sessionId);
          session.handler.buildFinished(sessionId);
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
    final BuildSession data = mySessions.get(sessionId);
    return data != null ? data.myChannel : null;
  }

  public boolean isCancelled(UUID sessionId) {
    return myCanceledSessions.contains(sessionId);
  }

  public void projectClosed(@NotNull Project project) {
    String projectFilePath = project.getProjectFilePath();
    for (BuildSession buildSession : mySessions.values()) {
      if (projectFilePath.equals(buildSession.myProjectFilePath)) {
        if (buildSession.state != BuildSessionState.IDLE) {
          LOG.error("SessionData in strange state=" + buildSession.state + " for project " + projectFilePath);
        }
        buildSession.closeChannel();
      }
    }
  }

}
