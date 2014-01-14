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

import com.google.common.base.Objects;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.Range;
import com.intellij.util.containers.HashSet;
import gnu.trove.TIntObjectHashMap;

import java.util.*;

/**
 * @author Vojtech Krasa
 */
public abstract class MultiEditAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.actionSystem.MultiEditAction");

  public static final Key<Object> PROCESSING = Key.create("MultiEditAction.Processing");

  private MultiEditAction() {
  }

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
      if (userDataHolder.getUserData(PROCESSING) != null ||
          Editor.SHOWING_LOOKUP.get(editor) != null ||
          editor.getUserData(PROCESSING) != null) {
        executeHandler.run();
        return;
      }
      else {
        //todo SmartEnter dirty fix, those processors do not use action's DataContext. see com.intellij.codeInsight.editorActions.smartEnter.JavaSmartEnterProcessor.plainEnter()
        editor.putUserData(PROCESSING, "1");
        
        clearEditorContext = true;
        userDataHolder.putUserData(PROCESSING, "1");
      }
    }

    try {
      SelectionModel selectionModel = editor.getSelectionModel();
      CaretModel caretModel = editor.getCaretModel();
      final List<CaretModelWithSelection> caretsAndSelections = getAndRemoveMultiEditRanges(editor);
      LOG.debug("executing with " + caretsAndSelections.size() + " carets");
      if (caretsAndSelections.size() <= 1) {
        executeHandler.run();
      }
      else {
        MultiEditListener multiEditListener = (MultiEditListener)caretModel;
        multiEditListener.beforeMultiCaretsExecution();
        try {
          for (CaretModelWithSelection caretModelWithSelection : caretsAndSelections) {
            caretModel.setActiveCaret(caretModelWithSelection.myCaretModel);
            if (caretModelWithSelection.isCaretOnly()) {
              selectionModel.removeSelection();
            }
            else {
              selectionModel.setSelection(caretModelWithSelection.mySelection.getFrom(), caretModelWithSelection.mySelection.getTo());
            }

            executeHandler.run();

            if (selectionModel.hasSelection()) {
              selectionModel.addMultiSelection(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd(), null, false);
            }
          }
        }
        finally {
          multiEditListener.afterMultiCaretsExecution();
        }
      }
    }
    finally {
      if (clearEditorContext) {
        editor.putUserData(PROCESSING, null);
      }
    }
  }

  public static List<CaretModelWithSelection> getAndRemoveMultiEditRanges(Editor editor) {
    SelectionModel selectionModel = editor.getSelectionModel();
    CaretModel caretModel = editor.getCaretModel();


    final List<Range<Integer>> multiSelects = selectionModel.getMultiSelections();
    selectionModel.removeMultiSelections();
    return merge(caretModel.getMultiCarets(), multiSelects);
  }


  public static List<CaretModelWithSelection> merge(List<CaretModel> carets, List<Range<Integer>> selections) {
    TIntObjectHashMap<Range<Integer>> selectionsMap = new TIntObjectHashMap<Range<Integer>>();
    for (Range<Integer> integerRange : selections) {
      selectionsMap.put(integerRange.getFrom(), integerRange);
      selectionsMap.put(integerRange.getTo(), integerRange);
    }

    List<CaretModelWithSelection> caretModelWithSelections = new ArrayList<CaretModelWithSelection>(carets.size());
    for (CaretModel caret : carets) {
      Range<Integer> selection = selectionsMap.get(caret.getOffset());
      caretModelWithSelections.add(new CaretModelWithSelection(caret, selection));
    }
    Collections.sort(caretModelWithSelections);

    return caretModelWithSelections;
  }

  public static Collection<Integer> getMultiCaretOffsets(final Editor editor) {
    Set<Integer> offsets = new HashSet<Integer>();
    for (CaretModel caret : editor.getCaretModel().getMultiCarets()) {
      offsets.add(caret.getOffset());
    }

    return offsets;
  }

  public static class CaretModelWithSelection implements Comparable<CaretModelWithSelection> {
   private  CaretModel myCaretModel;
   private  Range<Integer> mySelection;

    public CaretModelWithSelection(CaretModel caretModel, Range<Integer> selection) {
      myCaretModel = caretModel;
      mySelection = selection;
    }

    public CaretModel getCaretModel() {
      return myCaretModel;
    }

    public Range<Integer> getSelection() {
      return mySelection;
    }

    private boolean isCaretOnly() {
      return mySelection == null;
    }

    @Override
    public int compareTo(CaretModelWithSelection o) {
      Integer offset = o.myCaretModel.getOffset();
      return offset.compareTo(this.myCaretModel.getOffset());
    }

    @Override
    public String toString() {
      return Objects.toStringHelper(this).add("caretOffset", myCaretModel.getOffset()).add("selection", mySelection).toString();
    }
  }


}
