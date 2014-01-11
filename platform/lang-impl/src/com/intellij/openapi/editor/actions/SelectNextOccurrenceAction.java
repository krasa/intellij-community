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
import java.util.List;

import static com.intellij.openapi.editor.SelectionModel.Direction.getDirection;

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

      List<Range<Integer>> multiSelections = new ArrayList<Range<Integer>>(selectionModel.getMultiSelections());
      List<Integer> carets = new ArrayList<Integer>(caretModel.getMultiCaretOffsets());

      addNormalCaretAndSelectionWhenNotUsingMultiEdit(selectionModel, caretModel, multiSelections, carets);

      final List<Range<Integer>> caretsAndSelections = MultiEditAction.merge(carets, multiSelections);
      boolean hadSingleCarets = selectWordAtAllCarets(editor, multiSelections, caretsAndSelections, dataContext);

      if (!hadSingleCarets) {
        selectNextOccurrence(editor, project, dataContext, multiSelections,carets);
      }
    }

    protected void addNormalCaretAndSelectionWhenNotUsingMultiEdit(SelectionModel selectionModel,
                                                                   CaretModel caretModel,
                                                                   List<Range<Integer>> multiSelections,
                                                                   List<Integer> carets) {
      //add normal selections/carets when multi edit is not used
      if (selectionModel.hasSelection() && multiSelections.isEmpty()) {
        final int selectionStart = selectionModel.getSelectionStart();
        final SelectionModel.Direction direction = getDirection(selectionStart == caretModel.getOffset());
        final int selectionEnd = selectionModel.getSelectionEnd();
        selectionModel.addMultiSelection(selectionStart, selectionEnd, direction, false);
        multiSelections.add(new Range<Integer>(selectionStart, selectionEnd));
        carets.add(direction == SelectionModel.Direction.LEFT ? selectionStart : selectionEnd);
      }
      else if (carets.isEmpty()) {
        caretModel.addMultiCaret(caretModel.getOffset());
        carets.add(caretModel.getOffset());
      }
    }

    protected boolean selectWordAtAllCarets(Editor editor,
                                            List<Range<Integer>> multiSelections,
                                            List<Range<Integer>> caretsAndSelections,
                                            DataContext dataContext) {
      final SelectionModel selectionModel = editor.getSelectionModel();
      final CaretModel caretModel = editor.getCaretModel();

      boolean hadSingleCarets = false;
      Integer bottomCaretOffset = null;
      for (int i = 0; i < caretsAndSelections.size(); i++) {
        Range<Integer> integerRange = caretsAndSelections.get(i);
        if (isCaret(integerRange)) {
          //continue if is part of selection
          if (isSelectionCaret(caretsAndSelections, i, integerRange)) {
            if (bottomCaretOffset == null) {
              bottomCaretOffset = integerRange.getTo();
            }
            continue;
          }
        }
        else {
          continue;
        }
        final Integer caretOffset = integerRange.getFrom();
        caretModel.moveToOffset(caretOffset);
        hadSingleCarets = true;

        if (selectionModel.hasSelection()) {
          selectionModel.removeSelection();
        }
        selectWordAtCaret(editor, multiSelections, dataContext);
        if (bottomCaretOffset == null) {
          bottomCaretOffset = caretModel.getOffset();
        }
      }

      //bottom selection should be the main one
      if (hadSingleCarets && multiSelections.size() > 0) {
        Collections.sort(multiSelections, MultiEditAction.RANGE_COMPARATOR);
        final Range<Integer> integerRange = multiSelections.get(0);
        selectionModel.setSelection(integerRange.getFrom(), integerRange.getTo());
        caretModel.moveToOffset(bottomCaretOffset);
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
                                        List<Integer> carets) {
      final SelectionModel selectionModel = editor.getSelectionModel();

      FindResult wordAtCaret = FindUtil.findWordAtCaret(project, editor);
      //find next if found one is already selected
      while (wordAtCaret != null &&
             wordAtCaret.isStringFound() &&
             multiSelections.contains(new Range<Integer>(wordAtCaret.getStartOffset(), wordAtCaret.getEndOffset()))) {
        wordAtCaret = FindUtil.findWordAtCaret(project, editor);
      }

      if (wordAtCaret != null && wordAtCaret.isStringFound()) {
        selectionModel.addMultiSelection(wordAtCaret.getStartOffset(), wordAtCaret.getEndOffset(), SelectionModel.Direction.RIGHT, false);
      }
      else {//search from start of the document
        FindUtil.searchAgain(project, editor, dataContext);

        if (selectionModel.hasSelection()) {
          if (multiSelections.contains(new Range<Integer>(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd()))) {
            // side effect workaround , #searchAgain scrolls to caret, so when everything is selected it is not so nice as it scrolls like crazy
            Collections.sort(carets);
            editor.getCaretModel().moveToOffset(carets.get(0));
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

    protected boolean isSelectionCaret(List<Range<Integer>> caretsAndSelections, int i, Range<Integer> caret) {
      final boolean caretOnStartOfSelection = i > 0 && caretsAndSelections.get(i - 1).getFrom().equals(caret.getTo());
      final boolean caretOnEndOfSelection =
        caretsAndSelections.size() > i + 1 && caretsAndSelections.get(i + 1).getTo().equals(caret.getTo());
      return caretOnStartOfSelection || caretOnEndOfSelection;
    }

    protected boolean isCaret(Range<Integer> integerRange) {
      return integerRange.getFrom().equals(integerRange.getTo());
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
