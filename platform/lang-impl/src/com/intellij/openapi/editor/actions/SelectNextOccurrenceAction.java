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
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.MultiEditAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Range;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Vojtech Krasa
 */
public class SelectNextOccurrenceAction extends EditorAction {
  protected static class Handler extends EditorActionHandler {

    @Override
    public void execute(Editor editor, DataContext dataContext) {
      Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(editor.getComponent()));
      final SelectionModel selectionModel = editor.getSelectionModel();
      final CaretModel caretModel = editor.getCaretModel();

      List<CaretModel> carets = new ArrayList<CaretModel>(caretModel.getMultiCarets());
      List<Range<Integer>> multiSelections = new ArrayList<Range<Integer>>(selectionModel.getMultiSelections());
      
      setNormalSelectionAsMulti(selectionModel, multiSelections);

      final List<MultiEditAction.CaretModelWithSelection> caretsAndSelections = MultiEditAction.merge(carets, multiSelections);

      boolean hadSingleCarets = selectWordAtAllCarets(editor, caretsAndSelections, dataContext);

      if (!hadSingleCarets) {
        selectNextOccurrence(editor, project, caretsAndSelections, multiSelections);
      }
    }

    protected void setNormalSelectionAsMulti(SelectionModel selectionModel, List<Range<Integer>> multiSelections) {
      if (multiSelections.isEmpty() && selectionModel.hasSelection()) {
        multiSelections.add(new Range<Integer>(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd()));
        selectionModel.addMultiSelection(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd(), null, false);
      }
    }


    protected boolean selectWordAtAllCarets(Editor editor,
                                            List<MultiEditAction.CaretModelWithSelection> caretsAndSelections,
                                            DataContext dataContext) {
      final SelectionModel selectionModel = editor.getSelectionModel();
      final CaretModel caretModel = editor.getCaretModel();

      boolean hadSingleCarets = false;
      for (int i = 0; i < caretsAndSelections.size(); i++) {
        MultiEditAction.CaretModelWithSelection caretModelWithSelection = caretsAndSelections.get(i);
        //continue if is part of selection
        if (caretModelWithSelection.hasSelection()) {
          continue;
        }
        if (!isValidCaret(caretModel, caretModelWithSelection)) {
          continue;
        }
        caretModel.setActiveCaret(caretModelWithSelection.getCaretModel());
        hadSingleCarets = true;

        if (selectionModel.hasSelection()) {
          selectionModel.removeSelection();
        }
        selectWordAtCaret(editor, dataContext);
      }

      return hadSingleCarets;
    }

    protected void selectWordAtCaret(Editor editor, DataContext dataContext) {
      final SelectionModel selectionModel = editor.getSelectionModel();
      final EditorAction editorSelectWord =
        (EditorAction)ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
      editorSelectWord.getHandler().execute(editor, dataContext);

      if (selectionModel.hasSelection()) {
        final int selectionStart = selectionModel.getSelectionStart();
        final int selectionEnd = selectionModel.getSelectionEnd();
        selectionModel.addMultiSelection(selectionStart, selectionEnd, SelectionModel.Direction.RIGHT, false);
      }
    }

    protected void selectNextOccurrence(Editor editor,
                                        Project project,
                                        List<MultiEditAction.CaretModelWithSelection> caretModelWithSelections,
                                        List<Range<Integer>> multiSelections) {

      Collections.sort(caretModelWithSelections);
      final MultiEditAction.CaretModelWithSelection caretModelWithSelection = caretModelWithSelections.get(0);

      int searchFrom = Math.max(caretModelWithSelection.getCaretModel().getOffset(), caretModelWithSelection.getSelection().getTo());

      final FindModel findModel = getFindModel(editor, caretModelWithSelection);

      final FindManager findManager = FindManager.getInstance(project);

      FindResult findResult =
        FindUtil.findFirstInRange(TextRange.create(searchFrom, editor.getDocument().getTextLength()), editor, findManager, findModel);

      if (findResult != null) {
        editor.getSelectionModel()
          .addMultiSelection(findResult.getStartOffset(), findResult.getEndOffset(), SelectionModel.Direction.RIGHT, false);
        scrollToResult(findResult, editor);
      }
      else {
        searchFromTop(editor, multiSelections, findModel, findManager, caretModelWithSelections);
      }
    }

    private FindModel getFindModel(Editor editor, MultiEditAction.CaretModelWithSelection caretModelWithSelection) {
      Document document = editor.getDocument();
      CharSequence text = document.getCharsSequence();

      final FindModel findModel = new FindModel();
      findModel.setWholeWordsOnly(wholeWordsOnly(editor));
      findModel.setCaseSensitive(true);
      findModel.setStringToFind(
        text.subSequence(caretModelWithSelection.getSelection().getFrom(), caretModelWithSelection.getSelection().getTo()).toString());
      return findModel;
    }

    private void searchFromTop(Editor editor,
                               List<Range<Integer>> multiSelections, FindModel findModel,
                               FindManager findManager,
                               List<MultiEditAction.CaretModelWithSelection> caretModelWithSelections) {
      int searchFrom = 0;
      final MultiEditAction.CaretModelWithSelection caretModelWithSelection =
        caretModelWithSelections.get(0);
      final int searchTo = Math.min(caretModelWithSelection.getCaretModel().getOffset(), caretModelWithSelection.getSelection().getFrom());

      FindResult findResult;
      do {
        findResult = FindUtil.findFirstInRange(TextRange.create(searchFrom, searchTo), editor, findManager, findModel);
        if (findResult != null) {
          searchFrom = findResult.getEndOffset();
        }
      }
      while (findResult != null && searchFrom <= searchTo && isAlreadySelected(findResult, multiSelections));

      if (findResult != null) {
        editor.getSelectionModel()
          .addMultiSelection(findResult.getStartOffset(), findResult.getEndOffset(), SelectionModel.Direction.RIGHT, false);
        scrollToResult(findResult, editor);
      }
    }

    private boolean isAlreadySelected(FindResult findResult, List<Range<Integer>> multiSelections) {
      return multiSelections.contains(new Range<Integer>(findResult.getStartOffset(), findResult.getEndOffset()));
    }

    private void scrollToResult(FindResult findResult, Editor editor) {
      setActiveCaret(findResult.getEndOffset(), editor);
      editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
    }

    private void setActiveCaret(final int offset, Editor editor) {
      CaretModel caretModel = editor.getCaretModel();
      final List<CaretModel> multiCarets = caretModel.getMultiCarets();
      for (CaretModel multiCaret : multiCarets) {
        if (multiCaret.getOffset() == offset) {
          caretModel.setActiveCaret(multiCaret);
          return;
        }
      }
    }


    protected boolean wholeWordsOnly(Editor editor) {
      return !editor.getSelectionModel().hasSelection();
    }

    @Override
    public boolean isEnabled(Editor editor, DataContext dataContext) {
      Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(editor.getComponent()));
      return project != null;
    }
  }

  //todo maybe move into CaretModel?
  private static boolean isValidCaret(CaretModel caretModel, MultiEditAction.CaretModelWithSelection caretModelWithSelection) {
    return caretModel.getMultiCarets().contains(caretModelWithSelection.getCaretModel());
  }

  public SelectNextOccurrenceAction() {
    super(new Handler());
  }

  @Override
  protected boolean useMultiEdit() {
    return false;
  }
}
