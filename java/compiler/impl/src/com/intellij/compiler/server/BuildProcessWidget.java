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

import com.intellij.compiler.impl.CompilerPropertiesAction;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;

/**
 * @author Vojtech Krasa
 */
public class BuildProcessWidget implements StatusBarWidget, ProjectComponent {
  //TODO new icons
  private Icon IconRunning = IconLoader.getIcon("compileServer.png", BuildProcessWidget.class);
  private Icon IconStopped = IconLoader.getDisabledIcon(IconRunning);

  private final String id;
  private final Project myProject;
  private Presentation myPresentation = new Presentation();
  private boolean running = false;
  private boolean installed = false;
  private Timer timer;
  private StatusBar statusBar;
  protected BuildManager myBuildManager;

  public BuildProcessWidget(Project project, BuildManager buildManager) {
    myBuildManager = buildManager;
    this.id = "Java Build Process";
    this.myProject = project;
  }

  @NotNull
  @Override
  public String ID() {
    return id;
  }

  @Nullable
  @Override
  public WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return myPresentation;
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {

  }

  @Override
  public void dispose() {
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "BuildProcessWidget";
  }

  @Override
  public void projectOpened() {
    statusBar = WindowManager.getInstance().getStatusBar(myProject);
    timer = new Timer(1000, new IconUpdater());
    timer.setRepeats(true);
    timer.start();
  }

  @Override
  public void projectClosed() {
    timer.stop();
    removeWidget();
  }

  @Override
  public void initComponent() {

  }

  @Override
  public void disposeComponent() {

  }

  private class IconUpdater implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
      BuildSession session = myBuildManager.myBuildSessionPool.getSessionByProject(myProject);
      boolean runningBuildProcess = session != null && session.myChannel != null && session.myChannel.isActive();
      if (running != runningBuildProcess) {
        running = runningBuildProcess;
        updateWidget();
      }
      if (!installed && running) {
        installWidget();
      }
    }
  }

  private void updateWidget() {
    statusBar.updateWidget(id);
  }

  private void installWidget() {
    if (!installed) {
      statusBar.addWidget(this, "before Position", myProject);
      installed = true;
    }
  }

  private void removeWidget() {
    if (installed) {
      statusBar.removeWidget(id);
      installed = false;
    }
  }

  class Presentation implements IconPresentation {

    @NotNull
    @Override
    public Icon getIcon() {
      return running() ? IconRunning : IconStopped;
    }

    private boolean running() {
      return running;
    }

    @Nullable
    @Override
    public String getTooltipText() {
      return "Java Build Process";
    }

    @Nullable
    @Override
    public Consumer<MouseEvent> getClickConsumer() {
      return new Consumer<MouseEvent>() {
        @Override
        public void consume(MouseEvent e) {
          JBPopupFactory.ActionSelectionAid mnemonics = JBPopupFactory.ActionSelectionAid.MNEMONICS;
          DefaultActionGroup group = new DefaultActionGroup(new Stop(), Separator.getInstance(), new CompilerPropertiesAction());

          DataContext dataContext = DataManager.getInstance().getDataContext(e.getComponent());
          ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup("Java Build Process", group, dataContext, mnemonics, true);
          Dimension preferredSize = popup.getContent().getPreferredSize();
          Point at = new Point(0, -preferredSize.height);
          popup.show(new RelativePoint(e.getComponent(), at));
        }
      };
    }
  }

  private class Stop extends DumbAwareAction {
    public Stop() {
      super("&Stop", "Shutdown Java Build Process", AllIcons.Actions.Suspend);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      BuildSession session = myBuildManager.myBuildSessionPool.getSessionByProject(myProject);
      if (session != null) {
        session.closeChannel();
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(running);
    }
  }

}
