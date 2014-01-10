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

import com.intellij.codeInsight.editorActions.SelectWordUtil;
import com.intellij.find.FindResult;
import com.intellij.find.FindUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
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

import static com.intellij.openapi.editor.SelectionModel.Direction.getDirection;

/**
 * @author Vojtech Krasa
 */
public class SelectNextOccurrenceAction extends EditorAction {
  private static class Handler extends EditorActionHandler {
    @Override
    public void execute(Editor editor, DataContext dataContext) {
      Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(editor.getComponent()));
      final SelectionModel selectionModel = editor.getSelectionModel();
      final CaretModel caretModel = editor.getCaretModel();

      List<Range<Integer>> multiSelections = new ArrayList<Range<Integer>>(selectionModel.getMultiSelections());
      List<Integer> carets = new ArrayList<Integer>(caretModel.getMultiCaretOffsets());

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

      final List<Range<Integer>> caretsAndSelections = MultiEditAction.merge(carets, multiSelections);
      boolean hadSingleCarets = selectWordAtCarets(editor, multiSelections, caretsAndSelections);

      if (!hadSingleCarets) {
        selectNextOccurrence(editor, project, dataContext, multiSelections);
      }
    }

    /**
     * it searches for a word from last selection, which is either last added selection, or first selection after some editor action was executed(MultiEdit works from bottom up)
     */
    private void selectNextOccurrence(Editor editor, Project project, DataContext dataContext, List<Range<Integer>> multiSelections) {
      final SelectionModel selectionModel = editor.getSelectionModel();
      final int oldSelectionEnd = selectionModel.getSelectionEnd();
      
      FindResult wordAtCaret = FindUtil.findWordAtCaret(project, editor);
      //find next if already selected
      while (wordAtCaret != null && wordAtCaret.isStringFound() && multiSelections.contains(new Range<Integer>(wordAtCaret.getStartOffset(), wordAtCaret.getEndOffset()))) {
         wordAtCaret = FindUtil.findWordAtCaret(project, editor);
      }

      if (wordAtCaret != null && wordAtCaret.isStringFound()) {
        selectionModel.addMultiSelection(wordAtCaret.getStartOffset(), wordAtCaret.getEndOffset(), SelectionModel.Direction.RIGHT, false);
      }
      else {//search from start of the document
        FindUtil.searchAgain(project, editor, dataContext);
        
        if (selectionModel.hasSelection() ) {
          if (multiSelections.contains(new Range<Integer>(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd()))) {
            // side effect workaround , #searchAgain scrolls to caret, so when everything is selected it is not so nice as it scrolls like crazy
            editor.getCaretModel().moveToOffset(oldSelectionEnd);
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

    private boolean selectWordAtCarets(Editor editor, List<Range<Integer>> multiSelections, List<Range<Integer>> caretsAndSelections) {
      final SelectionModel selectionModel = editor.getSelectionModel();
      final CaretModel caretModel = editor.getCaretModel();

      boolean hadSingleCarets = false;
      Integer  bottomCaretOffset = null;
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
        selectWordAtCaret(editor, multiSelections);
        if (bottomCaretOffset == null) {
           bottomCaretOffset = caretModel.getOffset();
         }
      }
      
      //bottom selection should be the main one
      if (hadSingleCarets) {
        Collections.sort(multiSelections, MultiEditAction.RANGE_COMPARATOR);
        final Range<Integer> integerRange = multiSelections.get(0);
        selectionModel.setSelection(integerRange.getFrom(), integerRange.getTo());
        caretModel.moveToOffset(bottomCaretOffset);
      }
      return hadSingleCarets;
    }

    private boolean isSelectionCaret(List<Range<Integer>> caretsAndSelections, int i, Range<Integer> caret) {
      final boolean caretOnStartOfSelection = i > 0 && caretsAndSelections.get(i - 1).getFrom() .equals(caret.getTo());
      final boolean caretOnEndOfSelection = caretsAndSelections.size() > i + 1 && caretsAndSelections.get(i + 1).getTo().equals(caret.getTo()) ;
      return caretOnStartOfSelection||caretOnEndOfSelection;
    }

    private boolean isCaret(Range<Integer> integerRange) {
      return integerRange.getFrom().equals(integerRange.getTo());
    }

    private void selectWordAtCaret(Editor editor, List<Range<Integer>> multiSelections) {
      final SelectionModel selectionModel = editor.getSelectionModel();

      //copy paste from com.intellij.openapi.editor.actions.SelectWordAtCaretAction.DefaultHandler - without camel case
      Document document = editor.getDocument();
      CharSequence text = document.getCharsSequence();
      List<TextRange> ranges = new ArrayList<TextRange>();
      final int cursorOffset = editor.getCaretModel().getOffset();

      SelectWordUtil.addWordSelection(false, text, cursorOffset, ranges);

      if (ranges.isEmpty()) return;

      int startWordOffset = Math.max(0, ranges.get(0).getStartOffset());
      int endWordOffset = Math.min(ranges.get(0).getEndOffset(), document.getTextLength());

      if (startWordOffset >= selectionModel.getSelectionStart() &&
          selectionModel.getSelectionEnd() >= endWordOffset &&
          ranges.size() == 1) {
        startWordOffset = 0;
        endWordOffset = document.getTextLength();
      }
      selectionModel.setSelection(startWordOffset, endWordOffset);
      if (selectionModel.hasSelection()) {
        final int selectionStart = selectionModel.getSelectionStart();
        final int selectionEnd = selectionModel.getSelectionEnd();
        selectionModel.addMultiSelection(selectionStart, selectionEnd, SelectionModel.Direction.RIGHT, false);
        multiSelections.add(new Range<Integer>(selectionStart, selectionEnd));
      }
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
