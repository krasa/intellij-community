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
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertArrayEquals;

public class BuildSessionPoolTest {

  @Test
  public void testSortByIdleDate() throws Exception {
    List<BuildSession> idleSessions = new ArrayList<BuildSession>();
    idleSessions.add(getSessionData(null));
    idleSessions.add(getSessionData(0));
    idleSessions.add(getSessionData(1));
    idleSessions.add(getSessionData(2));
    idleSessions.add(getSessionData(null));
    idleSessions.add(getSessionData(1));
    idleSessions.add(getSessionData(3));
    idleSessions.add(getSessionData(null));
    Collections.sort(idleSessions, BuildSessionPool.SESSION_DATA_COMPARATOR);
    Long[] idleTimes = new Long[idleSessions.size()];

    for (int i = 0; i < idleSessions.size(); i++) {
      Date idleFrom = idleSessions.get(i).idleFrom;
      idleTimes[i] = idleFrom == null ? null : idleFrom.getTime();
    }
    //oldest first
    assertArrayEquals(new Long[]{0L, 1L, 1L, 2L, 3L, null, null, null}, idleTimes);
  }

  private BuildSession getSessionData(Integer date) {
    BuildSession e = new BuildSession(new UUID(1, 1), null, null, null, new GeneralCommandLine());
    if (date != null) {
      e.idleFrom = new Date(date);
    }
    return e;
  }
}