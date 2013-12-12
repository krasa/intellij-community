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

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * TODO : fix escape, live templates, caret display, ctrl+z
 *
 * @author Vojtech Krasa
 */
public class MultiEditAction extends AnAction {

  public static final Key<Object> OBJECT_KEY = Key.create("MultiEditAction");

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      return;
    }
    final CaretModel caretModel = editor.getCaretModel();

    editor.addEditorMouseListener(new EditorMouseAdapter() {

      @Override
      public void mouseClicked(EditorMouseEvent e) {
        removeAdditionalCarets(editor);
      }
    });

    addAdditionalCarets(editor, caretModel.getOffset());
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

  public static void addAdditionalCarets(Editor editor, int offset) {
    TextAttributes attributes = new TextAttributes();
    attributes.setBackgroundColor(Color.CYAN);
    if (editor.getDocument().getTextLength() == offset) {
      return;
    }
    editor.getMarkupModel()
      .addRangeHighlighter(offset, offset + 1, HighlighterLayer.MULTI_EDIT_CARET, attributes, HighlighterTargetArea.EXACT_RANGE);
  }

  public static void executeWithMultipleCursors(Runnable executeHandler, Editor editor, DataContext dataContext) {
    //e.g. backspace workaround
    if (dataContext instanceof UserDataHolder) {
      final UserDataHolder userDataHolder = (UserDataHolder)dataContext;
      if (userDataHolder.getUserData(OBJECT_KEY) != null) {
        executeHandler.run();
        return;
      }
      else {
        userDataHolder.putUserData(OBJECT_KEY, "1");
      }
    }
    else if (dataContext != null) {
      //e.g. enter workaround
      final Editor data = CommonDataKeys.EDITOR.getData(dataContext);
      if (data != null) {
        if (data.getUserData(OBJECT_KEY) != null) {
          executeHandler.run();
          return;
        }
        else {
          data.putUserData(OBJECT_KEY, "1");
        }
      }
    }


    CaretModel caretModel = editor.getCaretModel();
    List<Integer> offsets = getAdditionalCaretOffsets(editor);
    if (offsets.isEmpty()) {
      executeHandler.run();
    }
    else {
      Collections.sort(offsets);
      Collections.reverse(offsets);

      for (Integer offset : offsets) {
        caretModel.moveToOffset(offset);
        executeHandler.run();
        int afterAction = caretModel.getOffset();
        addAdditionalCarets(editor, afterAction);
      }
    }

    //e.g. enter workaround
    if (dataContext != null) {
      final Editor data = CommonDataKeys.EDITOR.getData(dataContext);
      if (data != null) {
        data.putUserData(OBJECT_KEY, null);
      }
    }
  }

  private static List<Integer> getAdditionalCaretOffsets(Editor editor) {
    final RangeHighlighter[] allHighlighters = editor.getMarkupModel().getAllHighlighters();
    List<Integer> offsets = new ArrayList<Integer>();
    for (RangeHighlighter highlighter : allHighlighters) {
      if (highlighter.getLayer() == HighlighterLayer.MULTI_EDIT_CARET) {
        offsets.add(highlighter.getStartOffset());
        editor.getMarkupModel().removeHighlighter(highlighter);
      }
    }
    return offsets;
  }

}
