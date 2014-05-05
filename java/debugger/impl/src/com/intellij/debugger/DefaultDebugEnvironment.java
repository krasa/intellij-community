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
package com.intellij.debugger;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;

/**
 * Created by IntelliJ IDEA.
 * User: michael.golubev
 */
public class DefaultDebugEnvironment implements DebugEnvironment {

  private final GlobalSearchScope mySearchScope;
  private final Project myProject;
  private final Executor myExecutor;
  private final ProgramRunner myRunner;
  private RunProfileState myState;
  private final RemoteConnection myRemoteConnection;
  private final boolean myPollConnection;
  private final RunProfile myRunProfile;

  public DefaultDebugEnvironment(Project project,
                                 Executor executor,
                                 ProgramRunner runner,
                                 RunProfile runProfile,
                                 RunProfileState state,
                                 RemoteConnection remoteConnection,
                                 boolean pollConnection) {
    myProject = project;
    myExecutor = executor;
    myRunner = runner;
    myRunProfile = runProfile;
    myState = state;
    myRemoteConnection = remoteConnection;
    myPollConnection = pollConnection;

    mySearchScope = SearchScopeProvider.createSearchScope(project, runProfile);
  }

  @Override
  public ExecutionResult createExecutionResult() throws ExecutionException {
    return myState.execute(myExecutor, myRunner);
  }

  @Override
  public GlobalSearchScope getSearchScope() {
    return mySearchScope;
  }

  @Override
  public boolean isRemote() {
    return myState instanceof RemoteState;
  }

  @Override
  public RemoteConnection getRemoteConnection() {
    return myRemoteConnection;
  }

  @Override
  public boolean isPollConnection() {
    return myPollConnection;
  }

  @Override
  public String getSessionName() {
    return myRunProfile.getName();
  }
}
