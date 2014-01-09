package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Getter;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.text.JTextComponent;
import java.util.ArrayList;

public abstract class SelectAllAction extends EditorHeaderAction implements DumbAware {
  public SelectAllAction(EditorSearchComponent editorSearchComponent, Getter<JTextComponent> editorTextField) {
    super(editorSearchComponent);
    getTemplatePresentation().setIcon(AllIcons.Actions.Selectall);
    getTemplatePresentation().setDescription("Select all matches in editor");
    getTemplatePresentation().setText("Select All");

    ArrayList<Shortcut> shortcuts = new ArrayList<Shortcut>();
    ContainerUtil.addAll(shortcuts, ActionManager.getInstance().getAction(IdeActions.ACTION_SELECT_NEXT_OCCURRENCE).getShortcutSet()
      .getShortcuts());
    registerShortcutsForComponent(shortcuts, editorTextField.get(), this);
  }

  @Override
  public void update(final AnActionEvent e) {
    super.update(e);
    Editor editor = getEditorSearchComponent().getEditor();
    Project project = editor.getProject();
    if (project != null) {
      e.getPresentation().setEnabled(
        getEditorSearchComponent().hasMatches() && PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()) != null);
    }
  }

}
