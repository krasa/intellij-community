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
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import org.jetbrains.jps.api.CmdlineProtoUtil;
import org.jetbrains.jps.api.CmdlineRemoteProto;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author Vojtech Krasa
 */
final class BuildSession {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.server.BuildSession");

  final UUID sessionId;
  final BuilderMessageHandler handler;
  final String myProjectFilePath;
  final String myProcessCommandLine;
  volatile CmdlineRemoteProto.Message.ControllerMessage params;
  volatile Channel myChannel;
  volatile BuildSessionState state = BuildSessionState.INIT;
  volatile Date idleFrom;

  BuildSession(UUID sessionId,
               BuilderMessageHandler handler,
               CmdlineRemoteProto.Message.ControllerMessage params,
               String projectFilePath,
               GeneralCommandLine processCommandLine) {
    this.sessionId = sessionId;
    this.handler = handler;
    this.params = params;
    this.myProjectFilePath = projectFilePath;
    this.myProcessCommandLine = processCommandLine.getCommandLineString().replace(sessionId.toString(), "#sessionId");
  }

  public void setState(BuildSessionState state) {
    LOG.debug("updating state to " + state + ", sessionId=" + sessionId);
    this.state = state;
    if (state == BuildSessionState.IDLE) {
      idleFrom = new Date();
    }
  }

  public BuildSessionState getState() {
    return state;
  }

  public boolean isWorking() {
    return state == BuildSessionState.WORKING;
  }

  public boolean isCommandLineChanged(BuildSession newSession) {
    return !this.myProcessCommandLine.equals(newSession.myProcessCommandLine);
  }

  public void requestBuild() {
    try {
      CmdlineRemoteProto.Message.ControllerMessage message =
        CmdlineRemoteProto.Message.ControllerMessage.newBuilder().setType(CmdlineRemoteProto.Message.ControllerMessage.Type.BUILD_REQUEST)
          .build();
      ChannelFuture channelFuture = myChannel.writeAndFlush(CmdlineProtoUtil.toMessage(sessionId, message));
      channelFuture.get(1, TimeUnit.SECONDS);
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * kills the build process
   */
  public void closeChannel() {
    Channel channel = myChannel;
    if (channel != null && channel.isOpen()) {
      LOG.debug("Closing channel for sessionId=" + sessionId + " project=" + myProjectFilePath);
      channel.close();
    }
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("SessionData{");
    sb.append("state=").append(state);
    sb.append(", idleFrom=").append(idleFrom);
    sb.append(", myProjectFilePath='").append(myProjectFilePath).append('\'');
    sb.append(", sessionId=").append(sessionId);
    sb.append(", channel=").append(myChannel);
    sb.append('}');
    return sb.toString();
  }
}
