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
package git4idea.settings;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import git4idea.config.UpdateMethod;
import org.jetbrains.annotations.NotNull;

/**
 * @author Kirill Likhodedov
 */
@State(name = "Git.Push.Settings", storages = {@Storage(file = StoragePathMacros.WORKSPACE_FILE)})
public class GitPushSettings implements PersistentStateComponent<GitPushSettings.State> {

  private State myState = new State();

  public static class State {
    public boolean myUpdateAllRoots = true;
    public UpdateMethod myUpdateMethod = UpdateMethod.MERGE;
  }

  public static GitPushSettings getInstance(Project project) {
    return ServiceManager.getService(project, GitPushSettings.class);
  }

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState = state;
  }

  public boolean shouldUpdateAllRoots() {
    return myState.myUpdateAllRoots;
  }
  
  public void setUpdateAllRoots(boolean updateAllRoots) {
    myState.myUpdateAllRoots = updateAllRoots;
  }

  @NotNull
  public UpdateMethod getUpdateMethod() {
    return myState.myUpdateMethod;
  }

  public void setUpdateMethod(@NotNull UpdateMethod updateMethod) {
    myState.myUpdateMethod = updateMethod;
  }

}
