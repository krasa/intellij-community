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

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterClient;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.tree.IElementType;
import kotlin.ranges.IntRange;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * MarkupModel is too expensive, it is probably never going to be fast enough for the console, use this for highlighting from HighlightingInputFilter and content type on input. 
 * Asynchronous Filters do not need to use this, it would be too complex, and unnecessary as number of their highlights will be always low (if GrepConsole will use HighlightingInputFilter.
 * - some of it is copy paste from IJ 2016...
 * <p>
 * TODO properly handle intervals. Imho we need some interval tree, that will split intervals when intersecting with another and keep list of contentTypes of overlapping intervals so we can merge them in the right order.
 * TODO consider using it for USER_INPUT, as was in IJ 2016, whatever makes the code cleaner and less buggy
 * TODO #rehighlightHyperlinksAndFoldings by HighlightingInputFilter must use it
 * TODO keep SYSTEM_ERR,SYSTEM_OUT... when clearing? perhaps make another list just for backing content types?
 */
class ConsoleHighlighter extends DocumentAdapter implements EditorHighlighter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.impl.ConsoleHighlighter");

  /**
   * Holds information about lexical division by offsets of the text already pushed to document.
   * <p/>
   * Target offsets are anchored to the document here.
   */
  private final List<PushedTokenInfo> myTokens = new ArrayList<>();
  private HighlighterClient myEditor;


  /**
   * assumed that tokens are ordered by offset in ascending order.
   */
  public void addToken(int startOffset, int endOffset, final TokenBuffer.TokenInfo tokenInfo) {
    ConsoleViewContentType contentType = tokenInfo.contentType;

    int lastEnd = startOffset;
    if (!myTokens.isEmpty()) {
      PushedTokenInfo lastToken = myTokens.get(myTokens.size() - 1);
      lastEnd = lastToken.endOffset;
    }

    //THIS IS ALL WRONG - must merge+split overlapping ones
    List<PushedTokenInfo> highlighters = createInputFilterHighlighters(tokenInfo, startOffset, endOffset);
    if (highlighters != null) {
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0; i < highlighters.size(); i++) {
        PushedTokenInfo highlighter = highlighters.get(i);
        if (enlargeLastToken(highlighter.startOffset, highlighter.endOffset, highlighter.myContentType)) {
          lastEnd = highlighter.endOffset;
          continue;
        }

        if (lastEnd < highlighter.startOffset) {
          myTokens.add(new PushedTokenInfo(contentType, lastEnd, highlighter.startOffset));
          lastEnd = highlighter.startOffset;
        }

        myTokens.add(new PushedTokenInfo(highlighter.myContentType, lastEnd, highlighter.endOffset));
        lastEnd = highlighter.endOffset;
      }
    }

    if (!enlargeLastToken(lastEnd, endOffset, contentType)) {
      myTokens.add(new PushedTokenInfo(contentType, lastEnd, endOffset));
    }
  }

  private boolean enlargeLastToken(int startOffset, int endOffset, ConsoleViewContentType contentType) {
    if (!myTokens.isEmpty()) {
      final PushedTokenInfo lastPushedToken = myTokens.get(myTokens.size() - 1);
      if (lastPushedToken.myContentType == contentType
          && lastPushedToken.endOffset == startOffset) {
        lastPushedToken.endOffset = endOffset; // optimization}
        return true;
      }
    }
    return false;
  }

  private List<PushedTokenInfo> createInputFilterHighlighters(@NotNull TokenBuffer.TokenInfo tokenInfo, int startOffset, int endOffset) {
    List<PushedTokenInfo> newTokens = null;
    int length = tokenInfo.length();
    int tokenOffset = tokenInfo.myHighlightersRangeOffset;
    if (tokenInfo.highlighters != null) {
      List<Pair<IntRange, ConsoleViewContentType>> highlighters = tokenInfo.highlighters;
      //noinspection ForLoopReplaceableByForEach
      newTokens = new ArrayList<>(highlighters.size());
      for (int i = 0; i < highlighters.size(); i++) {
        Pair<IntRange, ConsoleViewContentType> pair = highlighters.get(i);
        if (pair == null || pair.getFirst() == null || pair.second == null) {
          continue;
        }
        IntRange range = pair.getFirst();
        ConsoleViewContentType contentType = pair.getSecond();

        //adjusted to the actual token content
        int adjustedStart = Math.max(range.getStart() - tokenOffset, 0);
        int adjustedEnd = Math.max(range.getEndInclusive() - tokenOffset, 0);
        adjustedStart = Math.min(adjustedStart, length);
        adjustedEnd = Math.min(adjustedEnd, length);
        if (adjustedStart == adjustedEnd) {
          //text for which the range was created was cut out, or the range was not valid
          continue;
        }

        //adjusted by document offset
        int adjustedStartOffset = Math.min(adjustedStart + startOffset, endOffset);
        int adjustedEndOffset = Math.min(adjustedEnd + startOffset, endOffset);

        if (adjustedStartOffset == adjustedEndOffset) {
          //this should never happen, but just to be sure
          continue;
        }
        //addToken(adjustedStartOffset, adjustedEndOffset, contentType);
        newTokens.add(new PushedTokenInfo(contentType, adjustedStartOffset, adjustedEndOffset));
      }
    }
    return newTokens;
  }

  public void clear() {
    myTokens.clear();
  }

  public long tokensSize() {
    return myTokens.size();
  }

  static class PushedTokenInfo {
    final ConsoleViewContentType myContentType;
    int startOffset;
    int endOffset;

    PushedTokenInfo(final ConsoleViewContentType myTokenInfo, int startOffset, int endOffset) {
      this.myContentType = myTokenInfo;
      this.startOffset = startOffset;
      this.endOffset = endOffset;
    }


    public int getLength() {
      return endOffset - startOffset;
    }

    @Override
    public String toString() {
      return "[" + startOffset + ";" + endOffset + "] " + myContentType;
    }

    public int getStart() {
      return startOffset;
    }
  }


  /**
   * Utility method that allows to adjust ranges of the given tokens within the text removal.
   * <p/>
   * <b>Note:</b> it's assumed that the given tokens list is ordered by offset in ascending order.
   *
   * @param tokens      target tokens ordered by offset in ascending order
   * @param startOffset start offset of the removed text (inclusive)
   * @param endOffset   end offset of the removed text (exclusive)
   */
  public void updateTokensOnTextRemoval(int startOffset, int endOffset) {
    final int firstIndex = findTokenInfoIndexByOffset(startOffset);
    if (firstIndex >= myTokens.size()) {
      return;
    }

    int removedSymbolsNumber = endOffset - startOffset;
    boolean updateOnly = false;
    int removeIndexStart = -1;
    int removeIndexEnd = -1;
    final PushedTokenInfo firstToken = myTokens.get(firstIndex);

    if (startOffset == firstToken.startOffset) {
      // Removed range is located entirely at the first token.
      if (endOffset < firstToken.endOffset) {
        firstToken.endOffset -= removedSymbolsNumber;
        updateOnly = true;
      }
      // The first token is completely removed.
      else {
        removeIndexStart = removeIndexEnd = firstIndex;
      }
    }
    // Removed range is located entirely at the first token. 
    else if (endOffset <= firstToken.endOffset) {
      firstToken.endOffset -= removedSymbolsNumber;
      updateOnly = true;
    }

    for (int i = firstIndex + 1; i < myTokens.size(); i++) {
      final PushedTokenInfo tokenInfo = myTokens.get(i);
      if (updateOnly) {
        tokenInfo.startOffset -= removedSymbolsNumber;
        tokenInfo.endOffset -= removedSymbolsNumber;
        continue;
      }

      // We know that start offset lays before the current token, so, it's completely removed if end offset is not less than
      // the token's end offset.
      if (endOffset >= tokenInfo.endOffset) {
        if (removeIndexStart < 0) {
          removeIndexStart = i;
        }
        removeIndexEnd = i;
        continue;
      }

      // Update current token offsets and adjust ranges of all subsequent tokens.
      tokenInfo.startOffset = startOffset;
      tokenInfo.endOffset = startOffset + (tokenInfo.endOffset - endOffset);
      updateOnly = true;
    }

    if (removeIndexStart >= 0) {
      myTokens.subList(removeIndexStart, removeIndexEnd + 1).clear();
    }
  }

  /**
   * Searches given collection for the token that contains given offset.
   * <p/>
   * <b>Note:</b> it's assumed that the given tokens list is ordered by offset in ascending order.
   *
   * @param tokens target tokens ordered by offset in ascending order
   * @param offset target offset
   * @return index of the target token within the given list; given list length if no such token is found
   */
  public int findTokenInfoIndexByOffset(final int offset) {
    int low = 0;
    int high = myTokens.size() - 1;

    while (low <= high) {
      final int mid = (low + high) / 2;
      final PushedTokenInfo midVal = myTokens.get(mid);
      if (offset < midVal.startOffset) {
        high = mid - 1;
      }
      else if (offset >= midVal.endOffset) {
        low = mid + 1;
      }
      else {
        return mid;
      }
    }
    return myTokens.size();
  }

  @NotNull
  @Override
  public HighlighterIterator createIterator(final int startOffset) {
    final int startIndex = findTokenInfoIndexByOffset(startOffset);

    return new HighlighterIterator() {
      private int myIndex = startIndex;

      @Override
      public TextAttributes getTextAttributes() {
        TextAttributes attributes = atEnd() ? null : getTokenInfo().myContentType.getAttributes();
        return attributes;
      }

      @Override
      public int getStart() {
        int i;
        i = atEnd() ? 0 : getTokenInfo().startOffset;
        return i;
      }

      @Override
      public int getEnd() {
        int i;
        i = atEnd() ? 0 : getTokenInfo().endOffset;
        return i;
      }

      @Override
      public IElementType getTokenType() {
        return null;
      }

      @Override
      public void advance() {
        myIndex++;
      }

      @Override
      public void retreat() {
        myIndex--;
      }

      @Override
      public boolean atEnd() {
        return myIndex < 0 || myIndex >= myTokens.size();
      }

      @Override
      public Document getDocument() {
        return myEditor.getDocument();
      }

      private PushedTokenInfo getTokenInfo() {
        return myTokens.get(myIndex);
      }
    };
  }

  @Override
  public void setText(@NotNull final CharSequence text) {
  }

  @Override
  public void setEditor(@NotNull final HighlighterClient editor) {
    LOG.assertTrue(myEditor == null, "Highlighters cannot be reused with different editors");
    myEditor = editor;
  }

  @Override
  public void setColorScheme(@NotNull EditorColorsScheme scheme) {
  }
}

 
  