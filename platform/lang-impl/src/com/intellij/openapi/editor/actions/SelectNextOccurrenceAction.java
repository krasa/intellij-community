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

import com.intellij.find.FindResult;
import com.intellij.find.FindUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.MultiEditAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.Range;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Vojtech Krasa
 */
public class SelectNextOccurrenceAction extends EditorAction {
  protected static class Handler extends EditorActionHandler {

    public static final Comparator<CaretModel> OFFSET_COMPARATOR = new Comparator<CaretModel>() {
      @Override
      public int compare(CaretModel o1, CaretModel o2) {
        final Integer offset = o1.getOffset();
        return offset.compareTo(o2.getOffset());
      }
    };

    @Override
    public void execute(Editor editor, DataContext dataContext) {
      Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(editor.getComponent()));
      final SelectionModel selectionModel = editor.getSelectionModel();
      final CaretModel caretModel = editor.getCaretModel();

      List<Range<Integer>> multiSelections = new ArrayList<Range<Integer>>(selectionModel.getMultiSelections());
      List<CaretModel> carets = new ArrayList<CaretModel>(caretModel.getMultiCarets());


      final List<MultiEditAction.CaretModelWithSelection> caretsAndSelections = MultiEditAction.merge(carets, multiSelections);
      boolean hadSingleCarets = selectWordAtAllCarets(editor, multiSelections, caretsAndSelections, dataContext);

      if (!hadSingleCarets) {
        selectNextOccurrence(editor, project, dataContext, multiSelections, carets);
      }
    }


    protected boolean selectWordAtAllCarets(Editor editor,
                                            List<Range<Integer>> multiSelections,
                                            List<MultiEditAction.CaretModelWithSelection> caretsAndSelections,
                                            DataContext dataContext) {
      final SelectionModel selectionModel = editor.getSelectionModel();
      final CaretModel caretModel = editor.getCaretModel();

      boolean hadSingleCarets = false;
      for (int i = 0; i < caretsAndSelections.size(); i++) {
        MultiEditAction.CaretModelWithSelection caretModelWithSelection = caretsAndSelections.get(i);
        //continue if is part of selection
        if (caretModelWithSelection.selection != null) {
          continue;
        }

        caretModel.setActiveCaret(caretModelWithSelection.myCaretModel);
        hadSingleCarets = true;

        if (selectionModel.hasSelection()) {
          selectionModel.removeSelection();
        }
        selectWordAtCaret(editor, multiSelections, dataContext);
      }

      //bottom selection should be the main one
      if (hadSingleCarets && multiSelections.size() > 0) {
        Collections.sort(multiSelections, MultiEditAction.RANGE_COMPARATOR);
        final Range<Integer> integerRange = multiSelections.get(0);
        selectionModel.setSelection(integerRange.getFrom(), integerRange.getTo());
      }
      return hadSingleCarets;
    }

    protected void selectWordAtCaret(Editor editor, List<Range<Integer>> multiSelections, DataContext dataContext) {
      final SelectionModel selectionModel = editor.getSelectionModel();
      final EditorAction editorSelectWord =
        (EditorAction)ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
      editorSelectWord.getHandler().execute(editor, dataContext);

      if (selectionModel.hasSelection()) {
        final int selectionStart = selectionModel.getSelectionStart();
        final int selectionEnd = selectionModel.getSelectionEnd();
        selectionModel.addMultiSelection(selectionStart, selectionEnd, SelectionModel.Direction.RIGHT, false);
        multiSelections.add(new Range<Integer>(selectionStart, selectionEnd));
      }
    }

    /**
     * it searches for a word from last selection, which is either last added selection, or first selection after some editor action was executed(MultiEdit works from bottom up)
     */
    protected void selectNextOccurrence(Editor editor,
                                        Project project,
                                        DataContext dataContext,
                                        List<Range<Integer>> multiSelections,
                                        List<CaretModel> carets) {
      final SelectionModel selectionModel = editor.getSelectionModel();
      Collections.sort(carets, OFFSET_COMPARATOR);
      editor.getCaretModel().setActiveCaret(carets.get(carets.size() - 1));

      FindResult wordAtCaret = FindUtil.findWordAtCaret(project, editor, wholeWordsOnly(editor));
      //find next if found one is already selected
      while (wordAtCaret != null &&
             wordAtCaret.isStringFound() &&
             multiSelections.contains(new Range<Integer>(wordAtCaret.getStartOffset(), wordAtCaret.getEndOffset()))) {
        wordAtCaret = FindUtil.findWordAtCaret(project, editor, wholeWordsOnly(editor));
      }

      if (wordAtCaret != null && wordAtCaret.isStringFound()) {
        selectionModel.addMultiSelection(wordAtCaret.getStartOffset(), wordAtCaret.getEndOffset(), SelectionModel.Direction.RIGHT, false);
      }
      else {//search from start of the document
        FindUtil.searchAgain(project, editor, dataContext);

        if (selectionModel.hasSelection()) {
          if (multiSelections.contains(new Range<Integer>(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd()))) {
            // side effect workaround , #searchAgain scrolls to caret, so when everything is selected it is not so nice as it scrolls like crazy
            Collections.sort(carets, OFFSET_COMPARATOR);
            editor.getCaretModel().setActiveCaret(carets.get(0));
            editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
          }
          else {
            int startOffset = selectionModel.getSelectionStart();
            int endOffset = selectionModel.getSelectionEnd();
            selectionModel.addMultiSelection(startOffset, endOffset, SelectionModel.Direction.RIGHT, false);
          }
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

  public SelectNextOccurrenceAction() {
    super(new Handler());
  }

  @Override
  protected boolean useMultiEdit() {
    return false;
  }
}
