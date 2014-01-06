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

import com.intellij.find.FindUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.MultiEditAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.Range;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Vojtech Krasa
 */
public class SelectNextWordAtCaretAction extends EditorAction {
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
        final SelectionModel.Direction direction =
          SelectionModel.Direction.getDirection(selectionModel.getSelectionStart() == caretModel.getOffset());
        selectionModel.addMultiSelection(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd(), direction, false);
        multiSelections.add(new Range<Integer>(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd()));
        caretModel.addMultiCaret(caretModel.getOffset());
        carets.add(direction == SelectionModel.Direction.LEFT ? selectionModel.getSelectionStart() : selectionModel.getSelectionEnd());
      }
      else if (carets.isEmpty()) {
        caretModel.addMultiCaret(caretModel.getOffset());
        carets.add(caretModel.getOffset());
      }

      final List<Range<Integer>> caretsAndSelections = MultiEditAction.merge(carets, multiSelections);
      boolean hadSingleCarets = selectWordAtCarets(editor, dataContext, selectionModel, caretModel, multiSelections, caretsAndSelections);

      if (!hadSingleCarets) {
        selectNextOccupancy(editor, project, selectionModel, caretModel, multiSelections, dataContext);
      }
    }

    //TODO krasa whole words?
    private void selectNextOccupancy(Editor editor,
                                     Project project,
                                     SelectionModel selectionModel,
                                     CaretModel caretModel,
                                     List<Range<Integer>> multiSelections,
                                     DataContext dataContext) {
      Collections.sort(multiSelections, MultiEditAction.RANGE_COMPARATOR);
      for (int i = 0; i < multiSelections.size(); i++) {
        Range<Integer> selection = multiSelections.get(i);
        selectionModel.setSelection(selection.getFrom(), selection.getTo());
        caretModel.moveToOffset(selection.getTo());

        FindUtil.findWordAtCaret(project, editor);
        if (selectionModel.hasSelection()) {
          int startOffset = selectionModel.getSelectionStart();
          int endOffset = selectionModel.getSelectionEnd();
          editor.getSelectionModel().addMultiSelection(startOffset, endOffset, SelectionModel.Direction.RIGHT, false);
        }
        else {
          FindUtil.searchAgain(project, editor, dataContext);
          if (selectionModel.hasSelection()) {
            int startOffset = selectionModel.getSelectionStart();
            int endOffset = selectionModel.getSelectionEnd();
            editor.getSelectionModel().addMultiSelection(startOffset, endOffset, SelectionModel.Direction.RIGHT, false);
          }
        }
        break;
      }
    }

    private boolean selectWordAtCarets(Editor editor,
                                       DataContext dataContext,
                                       SelectionModel selectionModel,
                                       CaretModel caretModel,
                                       List<Range<Integer>> multiSelections,
                                       List<Range<Integer>> caretsAndSelections) {
      boolean hadSingleCarets = false;
      for (int i = 0; i < caretsAndSelections.size(); i++) {
        Range<Integer> integerRange = caretsAndSelections.get(i);
        if (integerRange.getFrom().equals(integerRange.getTo())) {
          //continue if is part of selection
          if (caretsAndSelections.size() > i + 1 && caretsAndSelections.get(i + 1).getTo() >= integerRange.getTo()) {
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
        selectWordAtCaret(editor, dataContext, selectionModel, multiSelections);
      }
      return hadSingleCarets;
    }

    private void selectWordAtCaret(Editor editor,
                                   DataContext dataContext,
                                   SelectionModel selectionModel,
                                   List<Range<Integer>> multiSelections) {
      final SelectWordAtCaretAction selectWordAtCaretAction = new SelectWordAtCaretAction();
      selectWordAtCaretAction.getHandler().execute(editor, dataContext);
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

  public SelectNextWordAtCaretAction() {
    super(new Handler());
  }

  @Override
  protected boolean useMultiEdit() {
    return false;
  }
}
