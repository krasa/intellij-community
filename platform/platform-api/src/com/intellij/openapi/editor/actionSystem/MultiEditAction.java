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
import com.intellij.openapi.editor.event.EditorMouseAdapter;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.containers.HashSet;

import java.awt.event.MouseEvent;
import java.util.*;

/**
 * Highlighters are used only because they can keep offsets up to date.
 *
 * @author Vojtech Krasa
 */
public class MultiEditAction extends AnAction {
  public static final TextAttributes TEXT_ATTRIBUTES = new TextAttributes();

  public static final Key<Object> ALREADY_PROCESSING = Key.create("MultiEditAction.alreadyProcessing");
  public static final Key<Object> LISTENER_PRESENT = Key.create("MultiEditAction.listenerPresent");

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      return;
    }

    if (editor.getUserData(LISTENER_PRESENT) == null) {
      editor.putUserData(LISTENER_PRESENT, "1");
      editor.addEditorMouseListener(new EditorMouseAdapter() {
        @Override
        public void mouseClicked(EditorMouseEvent e) {
          final MouseEvent mouseEvent = e.getMouseEvent();
          if (!mouseEvent.isShiftDown() && !mouseEvent.isAltDown()) {
            removeAdditionalCarets(editor);
          }
        }
      });
    }
    final CaretModel caretModel = editor.getCaretModel();
    addOrRemoveAdditionalCaret(editor, caretModel.getOffset());
  }

  public static void removeAdditionalCarets(Editor editor) {
    for (RangeHighlighter rangeHighlighter : editor.getMarkupModel().getAllHighlighters()) {
      if (rangeHighlighter.getLayer() == HighlighterLayer.MULTI_EDIT_CARET) {
        editor.getMarkupModel().removeHighlighter(rangeHighlighter);
      }
    }
  }

  public static boolean hasAdditionalCarets(Editor editor) {
    for (RangeHighlighter rangeHighlighter : editor.getMarkupModel().getAllHighlighters()) {
      if (rangeHighlighter.getLayer() == HighlighterLayer.MULTI_EDIT_CARET) {
        return true;
      }
    }
    return false;
  }

  public static void addOrRemoveAdditionalCaret(Editor editor, int offset) {
    final RangeHighlighter[] allHighlighters = editor.getMarkupModel().getAllHighlighters();
    boolean existed = false;
    for (RangeHighlighter highlighter : allHighlighters) {
      if (highlighter.getLayer() == HighlighterLayer.MULTI_EDIT_CARET && offset == highlighter.getStartOffset()) {
        existed = true;
        editor.getMarkupModel().removeHighlighter(highlighter);
      }
    }
    if (!existed) {
      addAdditionalCaret(editor, offset);
      editor.setMultiCaretsMode(true);
    }
  }

  public static void addAdditionalCaret(Editor editor, int offset) {
    if (editor.getDocument().getTextLength() < offset) {
      return;
    }
    editor.getMarkupModel()
      .addRangeHighlighter(offset, offset, HighlighterLayer.MULTI_EDIT_CARET, TEXT_ATTRIBUTES, HighlighterTargetArea.EXACT_RANGE);
    editor.setMultiCaretsMode(true);
  }

  public static Collection<Integer> getAdditionalCaretsOffsets(Editor editor) {
    final RangeHighlighter[] allHighlighters = editor.getMarkupModel().getAllHighlighters();
    Set<Integer> offsets = new HashSet<Integer>();
    for (RangeHighlighter highlighter : allHighlighters) {
      if (highlighter.getLayer() == HighlighterLayer.MULTI_EDIT_CARET) {
        offsets.add(highlighter.getStartOffset());
      }
    }
    return offsets;
  }

  private static List<Integer> getAdditionalCaretOffsetsAndRemoveThem(Editor editor) {
    final RangeHighlighter[] allHighlighters = editor.getMarkupModel().getAllHighlighters();
    Set<Integer> offsets = new HashSet<Integer>();
    for (RangeHighlighter highlighter : allHighlighters) {
      if (highlighter.getLayer() == HighlighterLayer.MULTI_EDIT_CARET) {
        offsets.add(highlighter.getStartOffset());
        editor.getMarkupModel().removeHighlighter(highlighter);
      }
    }
    return new ArrayList<Integer>(offsets);
  }

  public static void executeWithMultipleCursors(Runnable executeHandler, Editor editor, DataContext dataContext) {
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

    CaretModel caretModel = editor.getCaretModel();
    List<Integer> offsets = getAdditionalCaretOffsetsAndRemoveThem(editor);
    if (offsets.isEmpty()) {
      executeHandler.run();
    }
    else {
      if (editor.getSelectionModel().hasBlockSelection()) {
        editor.getSelectionModel().removeBlockSelection();
      }

      Collections.sort(offsets);
      Collections.reverse(offsets);

      for (Integer offset : offsets) {
        caretModel.moveToOffset(offset);
        executeHandler.run();
        int afterAction = caretModel.getOffset();
        addAdditionalCaret(editor, afterAction);
      }
    }
  }


}
