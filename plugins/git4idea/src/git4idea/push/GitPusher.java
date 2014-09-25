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
package git4idea.push;

import com.intellij.dvcs.push.PushSpec;
import com.intellij.dvcs.push.Pusher;
import com.intellij.dvcs.push.VcsPushOptionValue;
import com.intellij.openapi.project.Project;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

class GitPusher extends Pusher<GitRepository, GitPushSource, GitPushTarget> {

  @NotNull private final Project myProject;
  @NotNull private final GitRepositoryManager myRepositoryManager;

  GitPusher(@NotNull Project project) {
    myProject = project;
    myRepositoryManager = GitUtil.getRepositoryManager(project);
  }

  @Override
  public void push(@NotNull Map<GitRepository, PushSpec<GitPushSource, GitPushTarget>> pushSpecs,
                   @Nullable VcsPushOptionValue optionValue, boolean force) {
    GitPushResult result = new GitPushOperation(myProject, pushSpecs, (GitPushTagMode)optionValue, force).execute();
    GitPushResultNotification notification = GitPushResultNotification.create(myProject, result, myRepositoryManager.moreThanOneRoot());
    notification.notify(myProject);
  }

}
