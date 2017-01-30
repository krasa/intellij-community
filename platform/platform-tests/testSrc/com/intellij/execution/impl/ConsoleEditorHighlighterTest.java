/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.impl;

import com.intellij.execution.filters.HighlightingInputFilter;
import com.intellij.execution.impl.ConsoleEditorHighlighter.HighlighterToken;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ConsoleEditorHighlighterTest {
  private final ConsoleEditorHighlighter myHighlighter = new ConsoleEditorHighlighter();
  private List<HighlighterToken> expected;

  static ConsoleViewContentType NORMAL_OUTPUT = new ConsoleViewContentType("NORMAL_OUTPUT", new TextAttributes());

  ConsoleViewContentType HIGHLIGHT_1 = new ConsoleViewContentType("HIGHLIGHT_1", new TextAttributes(JBColor.BLACK, null, null, null, 1));
  ConsoleViewContentType HIGHLIGHT_2 =
    new ConsoleViewContentType("HIGHLIGHT_2", new TextAttributes(JBColor.RED, JBColor.BLACK, null, null, 1));
  ConsoleViewContentType HIGHLIGHT_3 =
    new ConsoleViewContentType("HIGHLIGHT_3", new TextAttributes(JBColor.GREEN, JBColor.RED, null, null, 1));
  ConsoleViewContentType HIGHLIGHT_4 = new ConsoleViewContentType("HIGHLIGHT_4", new TextAttributes(null, JBColor.BLACK, null, null, 1));

  private List<HighlightingInputFilter.ResultItem> highlighters;

  @Before
  public void setUp() throws Exception {
    expected = new ArrayList<>();
    highlighters = new ArrayList<>();
  }

  @Test
  public void noHighlight() throws Exception {
    expected.add(token(0, 100, NORMAL_OUTPUT));

    List<ConsoleEditorHighlighter.OrderedHighlighter> input = adjustAndSort(highlighters);
    assertThat(myHighlighter.transform_withMergingOfTextAttributes(NORMAL_OUTPUT, input, 100)).isEqualTo(expected);
  }

  private List<ConsoleEditorHighlighter.OrderedHighlighter> adjustAndSort(List<HighlightingInputFilter.ResultItem> highlighters) {
    TokenBuffer.TokenInfo info = new TokenBuffer.TokenInfo(NORMAL_OUTPUT, highlighters, new String(new char[100]), null, 0);
    return myHighlighter.adjustRangesAndSort(info);
  }

  @Test
  public void beginning() throws Exception {
    highlighters.add(new HighlightingInputFilter.ResultItem(0, 10, HIGHLIGHT_1));

    expected.add(token(0, 10, HIGHLIGHT_1));
    expected.add(token(10, 100, NORMAL_OUTPUT));

    List<ConsoleEditorHighlighter.OrderedHighlighter> input = adjustAndSort(highlighters);
    assertThat(myHighlighter.transform_withMergingOfTextAttributes(NORMAL_OUTPUT, input, 100)).isEqualTo(expected);
  }

  @Test
  public void middle() throws Exception {
    highlighters.add(new HighlightingInputFilter.ResultItem(5, 10, HIGHLIGHT_1));

    expected.add(token(0, 5, NORMAL_OUTPUT));
    expected.add(token(5, 10, HIGHLIGHT_1));
    expected.add(token(10, 100, NORMAL_OUTPUT));

    List<ConsoleEditorHighlighter.OrderedHighlighter> input = adjustAndSort(highlighters);
    assertThat(myHighlighter.transform_withMergingOfTextAttributes(NORMAL_OUTPUT, input, 100)).isEqualTo(expected);
  }


  @Test
  public void end() throws Exception {
    highlighters.add(new HighlightingInputFilter.ResultItem(90, 100, HIGHLIGHT_1));

    expected.add(token(0, 90, NORMAL_OUTPUT));
    expected.add(token(90, 100, HIGHLIGHT_1));

    List<ConsoleEditorHighlighter.OrderedHighlighter> input = adjustAndSort(highlighters);
    assertThat(myHighlighter.transform_withMergingOfTextAttributes(NORMAL_OUTPUT, input, 100)).isEqualTo(expected);
  }

  @Test
  public void invalid() throws Exception {
    highlighters.add(new HighlightingInputFilter.ResultItem(-100, -10, HIGHLIGHT_1));
    highlighters.add(new HighlightingInputFilter.ResultItem(-10, 0, HIGHLIGHT_1));
    highlighters.add(new HighlightingInputFilter.ResultItem(-10, 10, HIGHLIGHT_1));
    highlighters.add(new HighlightingInputFilter.ResultItem(90, 200, HIGHLIGHT_1));
    highlighters.add(new HighlightingInputFilter.ResultItem(200, 300, HIGHLIGHT_1));

    expected.add(token(0, 10, HIGHLIGHT_1));
    expected.add(token(10, 90, NORMAL_OUTPUT));
    expected.add(token(90, 100, HIGHLIGHT_1));

    List<ConsoleEditorHighlighter.OrderedHighlighter> input = adjustAndSort(highlighters);
    assertThat(myHighlighter.transform_withMergingOfTextAttributes(NORMAL_OUTPUT, input, 100)).isEqualTo(expected);
  }

  @Test
  public void overlapping_inside() throws Exception {
    highlighters.add(new HighlightingInputFilter.ResultItem(0, 15, HIGHLIGHT_1));
    highlighters.add(new HighlightingInputFilter.ResultItem(5, 10, HIGHLIGHT_2));

    expected.add(token(0, 5, HIGHLIGHT_1));
    expected.add(token(5, 10, HIGHLIGHT_1, HIGHLIGHT_2));
    expected.add(token(10, 15, HIGHLIGHT_1));
    expected.add(token(15, 100, NORMAL_OUTPUT));

    List<ConsoleEditorHighlighter.OrderedHighlighter> input = adjustAndSort(highlighters);
    assertThat(myHighlighter.transform_withMergingOfTextAttributes(NORMAL_OUTPUT, input, 100)).isEqualTo(expected);
  }



  @Test
  public void overlapping_insideWithHigherPriority() throws Exception {
    highlighters.add(new HighlightingInputFilter.ResultItem(5, 10, HIGHLIGHT_2));
    highlighters.add(new HighlightingInputFilter.ResultItem(0, 15, HIGHLIGHT_1));

    expected.add(token(0, 5, HIGHLIGHT_1));
    expected.add(token(5, 10, HIGHLIGHT_2, HIGHLIGHT_1));
    expected.add(token(10, 15, HIGHLIGHT_1));
    expected.add(token(15, 100, NORMAL_OUTPUT));

    List<ConsoleEditorHighlighter.OrderedHighlighter> input = adjustAndSort(highlighters);
    assertThat(myHighlighter.transform_withMergingOfTextAttributes(NORMAL_OUTPUT, input, 100)).isEqualTo(expected);
  }


  @Test
  public void overlapping() throws Exception {
    highlighters.add(new HighlightingInputFilter.ResultItem(0, 10, HIGHLIGHT_1));
    highlighters.add(new HighlightingInputFilter.ResultItem(5, 15, HIGHLIGHT_2));

    expected.add(token(0, 5, HIGHLIGHT_1));
    expected.add(token(5, 10, HIGHLIGHT_1, HIGHLIGHT_2));
    expected.add(token(10, 15, HIGHLIGHT_2));
    expected.add(token(15, 100, NORMAL_OUTPUT));

    List<ConsoleEditorHighlighter.OrderedHighlighter> input = adjustAndSort(highlighters);
    assertThat(myHighlighter.transform_withMergingOfTextAttributes(NORMAL_OUTPUT, input, 100)).isEqualTo(expected);
  }


  @Test
  public void overlapping_sameRange() throws Exception {
    highlighters.add(new HighlightingInputFilter.ResultItem(5, 10, HIGHLIGHT_1));
    highlighters.add(new HighlightingInputFilter.ResultItem(5, 10, HIGHLIGHT_2));

    expected.add(token(0, 5, NORMAL_OUTPUT));
    expected.add(token(5, 10, HIGHLIGHT_1, HIGHLIGHT_2));
    expected.add(token(10, 100, NORMAL_OUTPUT));

    List<ConsoleEditorHighlighter.OrderedHighlighter> input = adjustAndSort(highlighters);
    assertThat(myHighlighter.transform_withMergingOfTextAttributes(NORMAL_OUTPUT, input, 100)).isEqualTo(expected);
  }

  @Test
  public void overlapping_complicated() throws Exception {
    highlighters.add(new HighlightingInputFilter.ResultItem(3, 6, HIGHLIGHT_1));
    highlighters.add(new HighlightingInputFilter.ResultItem(3, 4, HIGHLIGHT_2));
    highlighters.add(new HighlightingInputFilter.ResultItem(3, 5, HIGHLIGHT_3));
    highlighters.add(new HighlightingInputFilter.ResultItem(5, 8, HIGHLIGHT_4));
    highlighters.add(new HighlightingInputFilter.ResultItem(2, 10, HIGHLIGHT_4));
    highlighters.add(new HighlightingInputFilter.ResultItem(8, 11, HIGHLIGHT_4));


    expected.add(token(0, 2, NORMAL_OUTPUT));
    expected.add(token(2, 3, HIGHLIGHT_4));
    expected.add(token(3, 4, HIGHLIGHT_1, HIGHLIGHT_2, HIGHLIGHT_3, HIGHLIGHT_4));
    expected.add(token(4, 5, HIGHLIGHT_1, HIGHLIGHT_3));
    expected.add(token(5, 6, HIGHLIGHT_1, HIGHLIGHT_4));
    expected.add(token(6, 8, HIGHLIGHT_4, HIGHLIGHT_4));
    expected.add(token(8, 10, HIGHLIGHT_4, HIGHLIGHT_4));
    expected.add(token(10, 11, HIGHLIGHT_4));
    expected.add(token(11, 100, NORMAL_OUTPUT));

    List<ConsoleEditorHighlighter.OrderedHighlighter> input = adjustAndSort(highlighters);
    assertThat(myHighlighter.transform_withMergingOfTextAttributes(NORMAL_OUTPUT, input, 100)).isEqualTo(expected);
  }


  @NotNull
  private static HighlighterToken token(int start, int end, ConsoleViewContentType... contentTypes) {
    if (contentTypes[0] == NORMAL_OUTPUT) {
      return new ConsoleEditorHighlighter.HighlighterToken_ContentType(NORMAL_OUTPUT, start, end);
    }
    return new ConsoleEditorHighlighter.HighlighterToken_TextAttributes(merge(contentTypes), start, end);
  }

  private static TextAttributes merge(ConsoleViewContentType... highlight) {
    TextAttributes merge = highlight[0].getAttributes();
    for (int i = 1; i < highlight.length; i++) {
      ConsoleViewContentType type = highlight[i];
      merge = ConsoleEditorHighlighter.merge(type.getAttributes(), merge);
    }
    return ConsoleEditorHighlighter.merge(NORMAL_OUTPUT.getAttributes(), merge);
  }
}