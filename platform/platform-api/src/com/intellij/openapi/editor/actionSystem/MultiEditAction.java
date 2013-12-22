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
public abstract class MultiEditAction  {

  public static final Key<Object> ALREADY_PROCESSING = Key.create("MultiEditAction.alreadyProcessing");

  private MultiEditAction() {
  }

//public void actionPerformed(AnActionEvent e) {
    //final DataContext dataContext = e.getDataContext();
    //final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    //if (editor == null) {
    //  return;
    //}
    //final CaretModel caretModel = editor.getCaretModel();
    //caretModel.addOrRemoveMultiCaret(caretModel.getOffset());
  //}


  public static Runnable wrapRunnable(final Runnable executeHandler, final Editor editor, final DataContext dataContext) {
    return new Runnable() {
      @Override
      public void run() {
        executeWithMultiEdit(executeHandler, editor, dataContext);
      }
    };
  }

  public static void executeWithMultiEdit(Runnable executeHandler, Editor editor, DataContext dataContext) {
    boolean clearEditorContext = false;
    //running that multiEdit logic twice is bad, unless dataContext is null - it means that we should run it always 
    if (dataContext instanceof UserDataHolder) {
      final UserDataHolder userDataHolder = (UserDataHolder)dataContext;
      //TODO maybe there is a better way how to find out if there is lookup, I just do not see com.intellij.codeInsight.lookup.LookupManager#getActiveLookup
      if (userDataHolder.getUserData(ALREADY_PROCESSING) != null ||
          Editor.SHOWING_LOOKUP.get(editor) != null ||
          editor.getUserData(ALREADY_PROCESSING) != null) {
        executeHandler.run();
        return;
      }
      else {
        //todo SmartEnter dirty fix, those processors do not use action's DataContext. see com.intellij.codeInsight.editorActions.smartEnter.JavaSmartEnterProcessor.plainEnter()
        editor.putUserData(ALREADY_PROCESSING, "1");
        clearEditorContext = true;
        userDataHolder.putUserData(ALREADY_PROCESSING, "1");
      }
    }

    try {
      SelectionModel selectionModel = editor.getSelectionModel();
      CaretModel caretModel = editor.getCaretModel();
      final List<Range<Integer>> caretsAndSelections = getMultiEditRanges(editor);

      if (caretsAndSelections.isEmpty()) {
        executeHandler.run();
      }
      else {
        for (int i = 0; i < caretsAndSelections.size(); i++) {
          Range<Integer> range = caretsAndSelections.get(i);
          SelectionModel.Direction direction = null;
          if (isCaret(range)) {
            direction = SelectionModel.Direction.RIGHT;
          }
          //merge overlapping selections and carets
          Range<Integer> nextRange = getNextRange(caretsAndSelections, i);
          while (nextRange != null && nextRange.getTo() >= range.getFrom()) {
            i++;
            range = new Range<Integer>(min(nextRange.getFrom(), range.getFrom()), range.getTo());
            if (direction == null && isCaret(nextRange)) {
              direction = determineDirection(range, nextRange);
            }
            nextRange = getNextRange(caretsAndSelections, i);
          }

          if (isCaret(range)) {
            selectionModel.removeSelection();
            caretModel.moveToOffset(range.getFrom());
          }
          else {
            selectionModel.setSelection(range.getFrom(), range.getTo());
            moveCaretToOffset(caretModel, range, direction);
          }

          executeHandler.run();

          if (selectionModel.hasSelection()) {
            boolean putCursorOnStart = selectionModel.getSelectionStart() == caretModel.getOffset();
            selectionModel.addMultiSelection(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd(),
                                             SelectionModel.Direction.getDirection(putCursorOnStart), true);
          }
          else {
            caretModel.addMultiCaret(caretModel.getOffset());
          }
        }
      }
    }
    finally {
      if (clearEditorContext) {
        editor.putUserData(ALREADY_PROCESSING, null);
      }
    }
  }

  public static List<Range<Integer>> getMultiEditRanges(Editor editor) {
    SelectionModel selectionModel = editor.getSelectionModel();
    CaretModel caretModel = editor.getCaretModel();


    final List<Range<Integer>> multiSelects = selectionModel.getMultiSelectionsAndRemoveThem();
    List<Integer> carets = new ArrayList<Integer>(caretModel.getMultiCaretOffsetsAndRemoveThem());
    return merge(carets, multiSelects);
  }

  private static SelectionModel.Direction determineDirection(Range<Integer> range, Range<Integer> nextRange) {
    SelectionModel.Direction direction = null;
    final Integer caretOffset = nextRange.getFrom();
    if (caretOffset.equals(range.getFrom())) {
      direction = SelectionModel.Direction.LEFT;
    }
    else if (caretOffset.equals(range.getTo())) {
      direction = SelectionModel.Direction.RIGHT;
    }
    return direction;
  }

  /* move caret on the right place so that shift+arrow work properly */
  private static void moveCaretToOffset(CaretModel caretModel, Range<Integer> range, SelectionModel.Direction direction) {
    if (direction == SelectionModel.Direction.LEFT) {
      caretModel.moveToOffset(range.getFrom());
    }
    else {//default right
      caretModel.moveToOffset(range.getTo());
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

  private static List<Range<Integer>> merge(List<Integer> carets, List<Range<Integer>> caretModel) {
    final ArrayList<Range<Integer>> merge = new ArrayList<Range<Integer>>(carets.size() + caretModel.size());
    merge.addAll(caretModel);
    for (Integer caretOffset : carets) {
      merge.add(new Range<Integer>(caretOffset, caretOffset));
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


}
