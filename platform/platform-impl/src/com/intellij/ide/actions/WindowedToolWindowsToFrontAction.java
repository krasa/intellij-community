package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import com.intellij.openapi.wm.impl.WindowedDecorator;

import javax.swing.*;
import java.util.Collection;

/**
 * @author Vojtech Krasa
 */
public class WindowedToolWindowsToFrontAction extends DumbAwareAction {

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    ToolWindowManagerImpl instance = (ToolWindowManagerImpl)ToolWindowManagerEx.getInstance(project);
    Collection<WindowedDecorator> windowedToolWindows = instance.getWindowedToolWindows();
    for (WindowedDecorator windowedToolWindow : windowedToolWindows) {
      if (project == windowedToolWindow.getProject()) {
        windowedToolWindow.getFrame().toFront();
        windowedToolWindow.getFrame().repaint();
      }
    }
    JFrame frame = WindowManager.getInstance().getFrame(project);
    frame.toFront();
    frame.repaint();
  }
  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    presentation.setEnabled(CommonDataKeys.PROJECT.getData(event.getDataContext()) != null);
  }
}
