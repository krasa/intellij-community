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
package com.intellij.compiler.actions.internal;

import com.intellij.compiler.actions.CompileActionBase;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.Project;

/**
 * @author Vojtech Krasa
 */
public class MakeProjectRepeatedlyAction extends CompileActionBase {

  public static final String ID = "RebuildProjectRepeatedlyAction";

  private boolean make = false;

  protected void doAction(DataContext dataContext, final Project project) {
    if (make) {
      make = false;
      return;
    }
    make = true;
    make(project);
  }

  private void make(final Project project) {
    CompilerManager.getInstance(project).make(new CompileStatusNotification() {
      public void finished(boolean aborted, int errors, int warnings, final CompileContext compileContext) {
        if (!make || project.isDisposed()) {
          return;
        }
        make(project);
      }
    });
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    if (!presentation.isEnabled()) {
      return;
    }
    Project project = CommonDataKeys.PROJECT.getData(event.getDataContext());
    presentation.setEnabled(project != null);
    if (make) {
      presentation.setText("Stop making");
    }
    else {
      presentation.setText("Make Project Repeatedly");
    }
  }

}