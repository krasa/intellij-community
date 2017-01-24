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
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterClient;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * MarkupModel is too expensive, it is probably never going to be fast enough for the console, use this for highlighting from HighlightingInputFilter and content type on input. 
 * Asynchronous Filters do not need to use this, it would be too complex, and unnecessary as number of their highlights will be always low (if GrepConsole will use HighlightingInputFilter.
 * - some of it is copy paste from IJ 2016...
 * <p>
 * TODO properly handle intervals. Imho we need some interval tree, that will split intervals when intersecting with another and keep list of contentTypes of overlapping intervals so we can merge them in the right order.
 * TODO consider using it for USER_INPUT, as was in IJ 2016, whatever makes the code cleaner and less buggy
 * TODO #rehighlightHyperlinksAndFoldings by HighlightingInputFilter should use it - HighlightingInputFilterAdapter puts them into MarkupModel
 * TODO keep SYSTEM_ERR,SYSTEM_OUT... when clearing? perhaps make another list just for backing content types?
 */
@SuppressWarnings("ForLoopReplaceableByForEach")
class ConsoleHighlighter extends DocumentAdapter implements EditorHighlighter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.impl.ConsoleHighlighter");

  /**
   * Holds information about lexical division by offsets of the text already pushed to document.
   * <p/>
   * Target offsets are anchored to the document here.
   *
   * assumed that tokens are ordered by offset in ascending order.
   */
  private final List<PushedTokenInfo> myTokens = new ArrayList<>();
  private HighlighterClient myEditor;


  public void addToken(int startOffset, int endOffset, final TokenBuffer.TokenInfo tokenInfo) {
    ConsoleViewContentType contentType = tokenInfo.contentType;

    List<OrderedItem> items = adjustAndSort(tokenInfo);
    List<PushedTokenInfo> pushedTokens = transform(contentType, items, endOffset - startOffset);

    for (int i = 0; i < pushedTokens.size(); i++) {
      PushedTokenInfo newToken = pushedTokens.get(i);

      //adjusted by document offset
      int adjustedStartOffset = Math.min(newToken.startOffset + startOffset, endOffset);
      int adjustedEndOffset = Math.min(newToken.endOffset + startOffset, endOffset);

      newToken.startOffset = adjustedStartOffset;
      newToken.endOffset = adjustedEndOffset;

      if (!enlargeLastToken(newToken)) {
        myTokens.add(newToken);
      }
    }
  }

  private boolean enlargeLastToken(PushedTokenInfo newToken) {
    if (!myTokens.isEmpty()) {
      final PushedTokenInfo lastPushedToken = myTokens.get(myTokens.size() - 1);
      if (lastPushedToken.myContentType == newToken.myContentType
          && lastPushedToken.endOffset == newToken.startOffset) {
        lastPushedToken.endOffset = newToken.endOffset; // optimization}
        return true;
      }
    }
    return false;
  }

  private List<PushedTokenInfo> transform(ConsoleViewContentType contentType,
                                          List<OrderedItem> items, int length) {
    List<PushedTokenInfo> pushedTokenInfos = new ArrayList<>(items == null ? 1 : items.size());
    int lastIndex = 0;
    if (items != null) {
      for (int i = 0; i < items.size(); i++) {
        OrderedItem item = items.get(i);
        int start = Math.max(item.getStartOffset(), lastIndex);
        int end = item.getEndOffset();

        if (start > end) {
          continue;
        }

        if (start > lastIndex) {
          pushedTokenInfos.add(new PushedTokenInfo(contentType, lastIndex, start));
        }

        for (int j = i + 1; j < items.size(); j++) {
          OrderedItem next = items.get(j);
          if (next.getStartOffset() <= end) {
            if (next.getOrder() < item.getOrder()) {
              end = next.getStartOffset();
            }
          }
          else {
            break;
          }
        }

        pushedTokenInfos.add(new PushedTokenInfo(item.getContentType(), start, end));
        lastIndex = end;
      }
    }
    if (lastIndex < length) {
      pushedTokenInfos.add(new PushedTokenInfo(contentType, lastIndex, length));
    }
    return pushedTokenInfos;
  }

  @Nullable
  private List<OrderedItem> adjustAndSort(TokenBuffer.TokenInfo tokenInfo) {
    List<OrderedItem> items = null;
    List<HighlightingInputFilter.ResultItem> highlighters = tokenInfo.highlighters;
    int i = 0;
    if (highlighters != null) {
      items = new ArrayList<>(highlighters.size());
      int tokenOffset = tokenInfo.myHighlightersRangeOffset;
      int length = tokenInfo.length();

      for (int i1 = 0; i1 < highlighters.size(); i1++) {
        HighlightingInputFilter.ResultItem item = highlighters.get(i1);

        int adjustedStart = Math.max(item.getStartOffset() - tokenOffset, 0);
        int adjustedEnd = Math.max(item.getEndOffset() - tokenOffset, 0);
        adjustedStart = Math.min(adjustedStart, length);
        adjustedEnd = Math.min(adjustedEnd, length);

        items.add(new OrderedItem(adjustedStart, adjustedEnd, item.getContentType(), i++));
      }
      Collections.sort(items, OrderedItem.MY_COMPARATOR);
    }
    return items;
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

  static class OrderedItem extends HighlightingInputFilter.ResultItem {
    private static final OrderedItemComparator MY_COMPARATOR = new OrderedItemComparator();

    int myOrder;

    public OrderedItem(int offset, int offset1, ConsoleViewContentType type, int order) {
      super(offset, offset1, type);
      this.myOrder = order;
    }

    public int getOrder() {
      return myOrder;
    }

    @Override
    public String toString() {
      return "OrderedItem{" +
             "myOrder=" + myOrder +
             "} " + super.toString();
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

  private static class OrderedItemComparator implements Comparator<OrderedItem> {
    @Override
    public int compare(OrderedItem o1, OrderedItem o2) {
      if (o1.getStartOffset() == o2.getStartOffset()) {
        return o1.getOrder() - o2.getOrder();
      }
      return o1.getStartOffset() - o2.getStartOffset();
    }
  }
}

 
  