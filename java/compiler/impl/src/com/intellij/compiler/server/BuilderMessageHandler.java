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

import io.netty.channel.Channel;
import org.jetbrains.jps.api.CmdlineRemoteProto;

import java.util.UUID;

/**
 * @author Eugene Zhuravlev
 *         Date: 4/25/12
 */
public interface BuilderMessageHandler {
  void buildStarted(UUID sessionId);
  
  /**
   * release lock on {@link org.jetbrains.jps.api.CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Type#BUILD_COMPLETED}
   */
  void handleBuildMessage(Channel channel, UUID sessionId, CmdlineRemoteProto.Message.BuilderMessage msg);

  /**
   * something failed, show error to the user and release lock
   */
  void handleFailure(UUID sessionId, CmdlineRemoteProto.Message.Failure failure);

  /**
   * silently release lock (eg build was cancelled before building)
   * todo rename, or delete?
   */
  void sessionTerminated(UUID sessionId);
}
