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
import com.intellij.openapi.editor.markup.AttributesFlyweight;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * MarkupModel is too expensive, it is probably never going to be fast enough for the console, use this for highlighting from HighlightingInputFilter and content type on input. 
 * Asynchronous Filters do not need to use this, it would be too complex, and unnecessary as number of their highlights will be always low (if GrepConsole will use HighlightingInputFilter.
 * - some of it is copy paste from IJ 2016...
 * <p>
 */
@SuppressWarnings("ForLoopReplaceableByForEach")
public class ConsoleEditorHighlighter extends DocumentAdapter implements EditorHighlighter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.impl.ConsoleHighlighter");

  /**
   * TODO make non static and clear it? not that it should ever contain many items...
   */
  private static Map<AttributesFlyweight, TextAttributes> cache = new HashMap<>();  
  /**
   * Holds information about lexical division by offsets of the text already pushed to document.
   * <p/>
   * Target offsets are anchored to the document here.
   *
   * assumed that tokens are ordered by offset in ascending order.
   */
  private final List<HighlighterToken> myHighlighters = new ArrayList<>();
  private final List<HighlighterToken> myContentTypes = new ArrayList<>();
  private HighlighterClient myEditor;
  private boolean broken;

  public void addToken(int startOffset, int endOffset, final TokenBuffer.TokenInfo tokenInfo) {

    HighlighterToken contentTypeToken = new HighlighterToken_ContentType(tokenInfo.contentType, startOffset, endOffset);
    if (!mergeWithLastToken(contentTypeToken, myContentTypes)) {
      myContentTypes.add(contentTypeToken);
    }

    addHighlight(startOffset, endOffset, tokenInfo);
  }

  public void addHighlight(int startOffset, int endOffset, final TokenBuffer.TokenInfo tokenInfo) {
    if (broken) {
      return;
    }
    try {
      List<OrderedHighlighter> items = adjustRangesAndSort(tokenInfo);
      List<HighlighterToken> tokens = transform_withMergingOfTextAttributes(tokenInfo.contentType, items, endOffset - startOffset);

      for (int i = 0; i < tokens.size(); i++) {
        HighlighterToken newToken = tokens.get(i);

        //adjusted by document offset
        int adjustedStartOffset = Math.min(newToken.startOffset + startOffset, endOffset);
        int adjustedEndOffset = Math.min(newToken.endOffset + startOffset, endOffset);

        newToken.startOffset = adjustedStartOffset;
        newToken.endOffset = adjustedEndOffset;

        if (!mergeWithLastToken(newToken, myHighlighters)) {
          myHighlighters.add(newToken);
        }
      }
    }
    catch (Exception e) {
      LOG.error(e);
      broken = true;
    }
  }

  private static boolean mergeWithLastToken(HighlighterToken newToken, List<HighlighterToken> tokens) {
    if (!tokens.isEmpty()) {
      final HighlighterToken lastPushedToken = tokens.get(tokens.size() - 1);
      if (lastPushedToken.getAttributes() == newToken.getAttributes()
          && lastPushedToken.endOffset == newToken.startOffset) {
        lastPushedToken.endOffset = newToken.endOffset; // optimization}
        return true;
      }
    }
    return false;
  }

  protected List<HighlighterToken> transform_withMergingOfTextAttributes(ConsoleViewContentType contentType,
                                                                         List<OrderedHighlighter> items,
                                                                         int length) {
    List<HighlighterToken> tokensToPush = new ArrayList<>(items == null ? 1 : items.size());
    int lastEnd = 0;
    if (items != null) {

      int remainingCycles = items.size() * 10;
      for (int i = 0; i < items.size(); i++) {
        if (--remainingCycles < 0) {
          throw new IllegalStateException("probably infinite cycle detected, length=" + length + ", items=" + items);
        }
        boolean processTokenAgain = false;
        List<OrderedHighlighter> overlapping = null;
        OrderedHighlighter current = items.get(i);
        int start = Math.max(current.getStartOffset(), lastEnd);
        int end = current.getEndOffset();

        if (start > end) {
          continue;
        }

        if (start > lastEnd) { //use original content type for not highlighted text 
          tokensToPush.add(new HighlighterToken_ContentType(contentType, lastEnd, start));
        }

        //find overlapping tokens to merge, reduce range according to them
        for (int j = i + 1; j < items.size(); j++) {
          OrderedHighlighter next = items.get(j);
          if (next.overlapsWith(start)) {
            //overlapping token to merge 
            if (overlapping == null) {
              overlapping = new ArrayList<>();
            }
            overlapping.add(next);

            if (next.endsBefore(end)) {
              //current one is longer, so we need to process the rest of it next time
              end = next.getEndOffset();
              processTokenAgain = true;
            }
          }
          else if (next.startsBetween(start, end)) {   //starts after 'start', but before 'end'
            // must split the current one and process merge next time 
            end = next.getStartOffset(); 
            processTokenAgain = true;
          }
        }

        if (processTokenAgain) {
          i--;
        }

        if (start == end) {  
          continue;
        }

        TextAttributes mergedAttributes;
        if (overlapping != null) {
          mergedAttributes = mergeTextAttributes(overlapping, current, contentType);
        }
        else {
          mergedAttributes = current.getContentType().getAttributes().clone();
          mergedAttributes = merge(contentType.getAttributes(), mergedAttributes);
        }

        tokensToPush.add(new HighlighterToken_TextAttributes(mergedAttributes, start, end));
        
        lastEnd = end;
      }
    }
    if (lastEnd < length) {
      tokensToPush.add(new HighlighterToken_ContentType(contentType, lastEnd, length));
    }
    return tokensToPush;
  }

  private static TextAttributes mergeTextAttributes(List<OrderedHighlighter> overlapping,
                                                    OrderedHighlighter current,
                                                    ConsoleViewContentType contentType) {
    overlapping.add(current);
    Collections.sort(overlapping, OrderedHighlighter.ORDER_COMPARATOR);

    TextAttributes mergedAttributes = overlapping.get(0).getContentType().getAttributes().clone();
    for (int i = 1; i < overlapping.size(); i++) {
      OrderedHighlighter token = overlapping.get(i);
      mergedAttributes = merge(token.getContentType().getAttributes(), mergedAttributes);
    }
    mergedAttributes = merge(contentType.getAttributes(), mergedAttributes);
    return mergedAttributes;
  }

  @Nullable
  protected List<OrderedHighlighter> adjustRangesAndSort(TokenBuffer.TokenInfo tokenInfo) {
    List<OrderedHighlighter> items = null;
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

        if (adjustedStart < 0 || adjustedEnd > length || adjustedStart == adjustedEnd) {
          LOG.warn(
            "Invalid range on " +
            item +
            " (adjustedStart=" +
            adjustedStart +
            ", adjustedEnd=" +
            adjustedEnd +
            ", textLength=" +
            length +
            ", tokenOffset=" +
            tokenOffset +
            ")");
          continue;
        }
        items.add(new OrderedHighlighter(adjustedStart, adjustedEnd, item.getContentType(), i++));
      }
      Collections.sort(items, OrderedHighlighter.MY_COMPARATOR);
    }
    return items;
  }

  public void clearAll() {
    myHighlighters.clear();
    myContentTypes.clear();
  }

  public void clearHighlighters() {
    myHighlighters.clear();
  }

  public ConsoleViewContentType findContentTypeByOffset(int offset) {
    int index = findTokenInfoIndexByOffset(offset, myContentTypes);
    if (index >= myContentTypes.size()) {
      return ConsoleViewContentType.NORMAL_OUTPUT;
    }
    HighlighterToken_ContentType info = (HighlighterToken_ContentType)myContentTypes.get(index);
    return info.contentType;
  }


  /**
   * strange class hierarchy for memory optimization - saving one field reference
   */
  static abstract class HighlighterToken {
    protected int startOffset;
    protected int endOffset;

    public HighlighterToken(int startOffset, int endOffset) {
      this.startOffset = startOffset;
      this.endOffset = endOffset;
    }

    public int getLength() {
      return endOffset - startOffset;
    }

    public int getStart() {
      return startOffset;
    }

    public abstract TextAttributes getAttributes();

    @Override
    public String toString() {
      return "[" + startOffset + ";" + endOffset + "] " + getAttributes();
    }
  }

  static class HighlighterToken_TextAttributes extends HighlighterToken {
    final TextAttributes mergedAttributes;

    HighlighterToken_TextAttributes(@NotNull final TextAttributes mergedAttributes, final int startOffset, final int endOffset) {
      super(startOffset, endOffset);

      TextAttributes cachedAttributes = cache.get(mergedAttributes.getFlyweight());
      if (cachedAttributes == null) {
        cache.put(mergedAttributes.getFlyweight(), mergedAttributes);
        cachedAttributes = mergedAttributes;
      }

      this.mergedAttributes = cachedAttributes;
    }

    public TextAttributes getAttributes() {
      return mergedAttributes;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      HighlighterToken_TextAttributes info = (HighlighterToken_TextAttributes)o;

      if (startOffset != info.startOffset) return false;
      if (endOffset != info.endOffset) return false;
      if (mergedAttributes != null ? !mergedAttributes.equals(info.mergedAttributes) : info.mergedAttributes != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = mergedAttributes != null ? mergedAttributes.hashCode() : 0;
      result = 31 * result + startOffset;
      result = 31 * result + endOffset;
      return result;
    }
  }

  static class HighlighterToken_ContentType extends HighlighterToken {
    final ConsoleViewContentType contentType;

    HighlighterToken_ContentType(@NotNull final ConsoleViewContentType myTokenInfo, final int startOffset, final int endOffset) {
      super(startOffset, endOffset);
      this.contentType = myTokenInfo;
    }


    @Override
    public String toString() {
      return "[" + startOffset + ";" + endOffset + "] " + (contentType);
    }


    public TextAttributes getAttributes() {
      //noinspection ConstantConditions
      return contentType.getAttributes();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      HighlighterToken_ContentType info = (HighlighterToken_ContentType)o;

      if (startOffset != info.startOffset) return false;
      if (endOffset != info.endOffset) return false;
      if (contentType != null ? !contentType.equals(info.contentType) : info.contentType != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = 0;
      result = 31 * result + (contentType != null ? contentType.hashCode() : 0);
      result = 31 * result + startOffset;
      result = 31 * result + endOffset;
      return result;
    }
  }

  static class OrderedHighlighter extends HighlightingInputFilter.ResultItem {
    private static final OrderedItemComparator MY_COMPARATOR = new OrderedItemComparator();
    private static final Comparator<OrderedHighlighter> ORDER_COMPARATOR = Comparator.comparingInt(OrderedHighlighter::getOrder);

    private static class OrderedItemComparator implements Comparator<OrderedHighlighter> {
      @Override
      public int compare(OrderedHighlighter o1, OrderedHighlighter o2) {
        if (o1.getStartOffset() == o2.getStartOffset()) {
          return o1.getOrder() - o2.getOrder();
        }
        return o1.getStartOffset() - o2.getStartOffset();
      }
    }

    int myOrder;

    public OrderedHighlighter(int offset, int endOffset, ConsoleViewContentType type, int order) {
      super(offset, endOffset, type);
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

    private boolean startsBefore(int end) {
      return getStartOffset() < end;
    }

    private boolean startsBeforeOrAt(int start) {
      return getStartOffset() <= start;
    }

    private boolean endsAfter(int start) {
      return start < getEndOffset();
    }

    private boolean endsBefore(int end) {
      return getEndOffset() < end;
    }

    private boolean startsAfter(int lastEnd) {
      return lastEnd < getStartOffset();
    }

    private boolean overlapsWith(int start) {
      return startsBeforeOrAt(start) && endsAfter(start);
    }

    private boolean startsBetween(int start, int end) {
      return startsAfter(start) && startsBefore(end);
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
    updateTokensOnTextRemovalInternal(startOffset, endOffset, myHighlighters);
    updateTokensOnTextRemovalInternal(startOffset, endOffset, myContentTypes);
  }

  private static void updateTokensOnTextRemovalInternal(int startOffset, int endOffset, List<HighlighterToken> tokens) {
    final int firstIndex = findTokenInfoIndexByOffset(startOffset, tokens);
    if (firstIndex >= tokens.size()) {
      return;
    }

    int removedSymbolsNumber = endOffset - startOffset;
    boolean updateOnly = false;
    int removeIndexStart = -1;
    int removeIndexEnd = -1;
    final HighlighterToken firstToken = tokens.get(firstIndex);

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

    for (int i = firstIndex + 1; i < tokens.size(); i++) {
      final HighlighterToken tokenInfo = tokens.get(i);
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
      tokens.subList(removeIndexStart, removeIndexEnd + 1).clear();
    }
  }

  /**
   * Searches given collection for the token that contains given offset.
   * <p/>
   * <b>Note:</b> it's assumed that the given tokens list is ordered by offset in ascending order.
   *
   * @param tokens target tokens ordered by offset in ascending order
   * @param offset target offset
   * @param tokens
   * @return index of the target token within the given list; given list length if no such token is found
   */
  private static int findTokenInfoIndexByOffset(final int offset, List<HighlighterToken> tokens) {
    int low = 0;
    int high = tokens.size() - 1;

    while (low <= high) {
      final int mid = (low + high) / 2;
      final HighlighterToken midVal = tokens.get(mid);
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
    return tokens.size();
  }

  @NotNull
  @Override
  public HighlighterIterator createIterator(final int startOffset) {
    final int startIndex = findTokenInfoIndexByOffset(startOffset, myHighlighters);

    return new HighlighterIterator() {
      private int myIndex = startIndex;

      @Override
      public TextAttributes getTextAttributes() {
        return atEnd() ? null : getTokenInfo().getAttributes();
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
        return myIndex < 0 || myIndex >= myHighlighters.size();
      }

      @Override
      public Document getDocument() {
        return myEditor.getDocument();
      }

      private HighlighterToken getTokenInfo() {
        return myHighlighters.get(myIndex);
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

  protected static TextAttributes merge(@NotNull TextAttributes under, @NotNull TextAttributes above) {
    if (above.getBackgroundColor() == null) {
      above.setBackgroundColor(under.getBackgroundColor());
    }
    if (above.getForegroundColor() == null) {
      above.setForegroundColor(under.getForegroundColor());
    }
    above.setFontType(above.getFontType() | under.getFontType());

    if (above.getEffectColor() == null) {
      above.setEffectColor(under.getEffectColor());
      above.setEffectType(under.getEffectType());
    }
    return above;
  }
}

 
  