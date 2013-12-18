/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.Range;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static java.lang.Math.min;

/**
 * Highlighters are used only because they can keep offsets up to date.
 *
 * @author Vojtech Krasa
 */
public class MultiEditAction extends AnAction {

  public static final Key<Object> ALREADY_PROCESSING = Key.create("MultiEditAction.alreadyProcessing");

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      return;
    }
    final CaretModel caretModel = editor.getCaretModel();
    caretModel.addOrRemoveAdditionalCaret(caretModel.getOffset());
  }


  public static void executeWithMultiEdit(Runnable executeHandler, Editor editor, DataContext dataContext) {
    //e.g. backspace workaround
    if (dataContext instanceof UserDataHolder) {
      final UserDataHolder userDataHolder = (UserDataHolder)dataContext;
      if (userDataHolder.getUserData(ALREADY_PROCESSING) != null) {
        executeHandler.run();
        return;
      }
      else {
        userDataHolder.putUserData(ALREADY_PROCESSING, "1");
      }
    }
    final SelectionModel selectionModel = editor.getSelectionModel();
    CaretModel caretModel = editor.getCaretModel();


    final List<Range<Integer>> multiSelects = getMultiSelects(editor);
    List<Integer> offsets = new ArrayList<Integer>(caretModel.getAdditionalCaretOffsetsAndRemoveThem());

    if (offsets.isEmpty() && multiSelects.isEmpty()) {
      executeHandler.run();
    }
    else {
      if (selectionModel.hasBlockSelection()) {
        selectionModel.removeBlockSelection();
      }
      final List<Range<Integer>> caretsAndSelections = merge(offsets, multiSelects);

      for (int i = 0; i < caretsAndSelections.size(); i++) {
        Range<Integer> range = caretsAndSelections.get(i);
        Direction direction = null;
        if (isCaret(range)) {
          direction = Direction.RIGHT;
        }
        Range<Integer> nextRange = getNextRange(caretsAndSelections, i);
        while (nextRange != null && nextRange.getTo() >= range.getFrom()) {
          i++;
          range = new Range<Integer>(min(nextRange.getFrom(), range.getFrom()), range.getTo());
          if (direction == null && isCaret(nextRange)) {
            if (nextRange.getFrom().equals(range.getFrom())) {
              direction = Direction.LEFT;
            }
            else {
              direction = Direction.RIGHT;
            }
          }
          nextRange = getNextRange(caretsAndSelections, i);
        }


        if (isCaret(range)) {
          selectionModel.removeSelection();
          caretModel.moveToOffset(range.getFrom());
        }
        else {
          selectionModel.setSelection(range.getFrom(), range.getTo());
          setCaret(caretModel, range, direction);
        }
        executeHandler.run();
        if (selectionModel.hasSelection()) {
          caretModel.addAdditionalCaret(caretModel.getOffset());
          selectionModel.addMultiSelection(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
        }
        else {
          caretModel.addAdditionalCaret(caretModel.getOffset());
        }
      }
    }
  }

  /* set caret on the right place so that shift+arrow work properly */
  private static void setCaret(CaretModel caretModel, Range<Integer> range, Direction direction) {
    if (direction == Direction.RIGHT) {
      caretModel.moveToOffset(range.getTo());
    }
    else {
      caretModel.moveToOffset(range.getFrom());
    }
  }

  private static Range<Integer> getNextRange(List<Range<Integer>> caretsAndSelections, int current) {
    final int next = current + 1;
    if (next == caretsAndSelections.size()) {
      return null;
    }
    return caretsAndSelections.get(next);
  }

  private static boolean isCaret(Range<Integer> caretsOrSelection) {
    return caretsOrSelection.getFrom().equals(caretsOrSelection.getTo());
  }

  private static List<Range<Integer>> merge(List<Integer> offsets, List<Range<Integer>> caretModel) {
    final ArrayList<Range<Integer>> merge = new ArrayList<Range<Integer>>(offsets.size() + caretModel.size());
    merge.addAll(caretModel);
    for (Integer offset : offsets) {
      merge.add(new Range<Integer>(offset, offset));
    }
    Collections.sort(merge, RANGE_COMPARATOR);
    return merge;
  }

  public static final Comparator<Range<Integer>> RANGE_COMPARATOR = new Comparator<Range<Integer>>() {
    @Override
    public int compare(Range<Integer> o1, Range<Integer> o2) {
      final Integer to = o1.getTo();
      final Integer to2 = o2.getTo();
      final int i = to2.compareTo(to);
      if (i == 0) {
        final Integer from = o1.getFrom();
        final Integer from2 = o2.getFrom();
        //caret before selection
        return from2.compareTo(from);
      }
      return i;
    }
  };

  enum Direction {
    LEFT, RIGHT
  }

  private static List<Range<Integer>> getMultiSelects(Editor editor) {
    List<Range<Integer>> rangeHighlighters = new ArrayList<Range<Integer>>();
    final MarkupModel markupModel = editor.getMarkupModel();
    for (RangeHighlighter rangeHighlighter : markupModel.getAllHighlighters()) {
      if (rangeHighlighter.getLayer() == HighlighterLayer.MULTI_EDIT_SELECTION) {
        rangeHighlighters.add(new Range<Integer>(rangeHighlighter.getStartOffset(), rangeHighlighter.getEndOffset()));
        markupModel.removeHighlighter(rangeHighlighter);
      }
    }
    return rangeHighlighters;
  }


}
