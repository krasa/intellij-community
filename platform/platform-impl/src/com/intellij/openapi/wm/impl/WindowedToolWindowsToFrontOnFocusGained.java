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
package com.intellij.openapi.wm.impl;

import com.intellij.ide.actions.WindowedToolWindowsToFrontAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFrame;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class WindowedToolWindowsToFrontOnFocusGained extends WindowAdapter {

  protected final Project project;

  public WindowedToolWindowsToFrontOnFocusGained(Project project) {
    this.project = project;
  }

  @Override
  public void windowGainedFocus(WindowEvent e) {
    //todo maybe make it configurable?

    if (e.getOppositeWindow() instanceof IdeFrame) {
      IdeFrame oppositeWindow = (IdeFrame)e.getOppositeWindow();
      if (oppositeWindow.getProject() != project) {
        WindowedToolWindowsToFrontAction.windowedToolWindowsToFront(project);
      }
    }
    else if (e.getOppositeWindow() == null) {
      WindowedToolWindowsToFrontAction.windowedToolWindowsToFront(project);
    }
  }
}
