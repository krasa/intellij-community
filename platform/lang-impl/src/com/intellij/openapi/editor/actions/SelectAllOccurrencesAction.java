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

package com.intellij.openapi.editor.actions;

import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindResult;
import com.intellij.find.FindUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;

import java.util.ArrayList;

/**
 * @author Vojtech Krasa
 */
public class SelectAllOccurrencesAction extends EditorAction {
  protected static class Handler extends EditorActionHandler {
    @Override
    public void execute(Editor editor, DataContext dataContext) {
      Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(editor.getComponent()));
      selectAllOccurrences(editor, dataContext, project);
    }

    protected void selectAllOccurrences(Editor editor, DataContext dataContext, Project project) {
      final SelectionModel selectionModel = editor.getSelectionModel();
      if (!selectionModel.hasSelection()) {
        final EditorAction editorSelectWord =
          (EditorAction)ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
        editorSelectWord.getHandler().execute(editor, dataContext);
      }
      Document document = editor.getDocument();
      CharSequence text = document.getCharsSequence();
      String textToFind = text.subSequence(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd()).toString();


      final FindModel findModel = new FindModel();
      findModel.setWholeWordsOnly(wholeWordsOnly());
      findModel.setCaseSensitive(true);
      findModel.setStringToFind(textToFind);


      final FindManager instance = FindManager.getInstance(project);
      final ArrayList<FindResult> results = new ArrayList<FindResult>();
      FindUtil.findInRange(TextRange.create(0, editor.getDocument().getTextLength()), editor, instance, findModel, results);

      for (FindResult result : results) {
        int startOffset = result.getStartOffset();
        int endOffset = result.getEndOffset();
        selectionModel.addMultiSelection(startOffset, endOffset, SelectionModel.Direction.RIGHT, false);
      }
    }

    protected boolean wholeWordsOnly() {
      return false;
    }

    @Override
    public boolean isEnabled(Editor editor, DataContext dataContext) {
      Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(editor.getComponent()));
      return project != null;
    }
  }

  public SelectAllOccurrencesAction() {
    super(new Handler());
  }

  @Override
  protected boolean useMultiEdit() {
    return false;
  }
}
