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
package com.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.LineSet;
import com.intellij.openapi.editor.impl.RangeMarkerTree;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.List;

/**
 * @author gregsh
 */
public abstract class EditorTextFieldCellRenderer implements TableCellRenderer, Disposable {

  private static final String MY_PANEL_PROPERTY = "EditorTextFieldCellRenderer.MyEditorPanel";

  public EditorTextFieldCellRenderer(Disposable parent) {
    Disposer.register(parent, this);
  }

  protected abstract EditorColorsScheme getColorScheme();

  protected abstract String getText(FontMetrics fontMetrics, JTable table, Object value, int row, int column);

  protected void customizeEditor(EditorEx editor, Object value, boolean selected, int row, int col) {
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    MyPanel panel = getEditorPanel(table);
    EditorEx editor = panel.myEditor;
    Font font = table.getFont();
    Font editorFont = editor.getColorsScheme().getFont(EditorFontType.PLAIN);
    if (!Comparing.equal(font, editorFont)) {
      editor.getColorsScheme().setEditorFontSize(font.getSize());
    }
    panel.setText(getText(((EditorImpl)editor).getFontMetrics(Font.PLAIN), table, value, row, column));

    ((EditorImpl)editor).setPaintSelection(isSelected);
    editor.getSelectionModel().setSelection(0, isSelected ? editor.getDocument().getTextLength() : 0);
    editor.getColorsScheme().setColor(EditorColors.SELECTION_BACKGROUND_COLOR, table.getSelectionBackground());
    editor.getColorsScheme().setColor(EditorColors.SELECTION_FOREGROUND_COLOR, table.getSelectionForeground());
    editor.setBackgroundColor(getCellBackgroundColor(getColorScheme(), table, isSelected, row));

    panel.setBorder(null); // prevents double border painting when ExtendedItemRendererComponentWrapper is used

    customizeEditor(editor, value, isSelected, row, column);
    return panel;
  }

  public static Color getCellBackgroundColor(EditorColorsScheme colorsScheme, JTable table, boolean isSelected, int row) {
    return isSelected ? table.getSelectionBackground() :
           table.getSelectionModel().getLeadSelectionIndex() == row ? colorsScheme.getColor(EditorColors.CARET_ROW_COLOR) :
           table.getBackground();
  }

  @NotNull
  private MyPanel getEditorPanel(JTable table) {
    MyPanel panel = (MyPanel)table.getClientProperty(MY_PANEL_PROPERTY);
    if (panel != null) {
      EditorColorsScheme scheme = panel.myEditor.getColorsScheme();
      if (scheme instanceof DelegateColorScheme) {
        ((DelegateColorScheme)scheme).setDelegate(getColorScheme());
      }
      return panel;
    }

    // reuse EditorTextField initialization logic
    EditorTextField field = new EditorTextField(new MyDocument(), null, FileTypes.PLAIN_TEXT);
    field.setSupplementary(true);
    field.addNotify(); // creates editor

    EditorEx editor = (EditorEx)ObjectUtils.assertNotNull(field.getEditor());
    editor.setRendererMode(true);

    editor.setColorsScheme(editor.createBoundColorSchemeDelegate(null));
    editor.getColorsScheme().setColor(EditorColors.CARET_ROW_COLOR, null);

    editor.getScrollPane().setBorder(null);

    panel = new MyPanel(editor);
    Disposer.register(this, panel);

    table.putClientProperty(MY_PANEL_PROPERTY, panel);
    return panel;
  }

  @Override
  public void dispose() {
  }

  private static class MyPanel extends CellRendererPanel implements Disposable {
    private static final String ABBREVIATION_SUFFIX = "\u2026"; // 2026 '...'
    private static final char RETURN_SYMBOL = '\u23ce';

    private final StringBuilder myDocumentTextBuilder = new StringBuilder();
    private final EditorEx myEditor;
    private Dimension myPreferredSize;

    public MyPanel(EditorEx editor) {
      add(editor.getContentComponent());
      this.myEditor = editor;
    }

    public void setText(String text) {
      setText(text, false);
    }

    @Override
    public Dimension getPreferredSize() {
      return myPreferredSize == null ? super.getPreferredSize() : myPreferredSize;
    }

    @Override
    protected void paintComponent(Graphics g) {
      if (getBorder() == null) return;
      Color oldColor = g.getColor();
      Rectangle clip = g.getClipBounds();
      g.setColor(myEditor.getBackgroundColor());
      Insets insets = getInsets();
      g.fillRect(0, 0, insets.left, clip.height);
      g.fillRect(clip.width - insets.left - insets.right, 0, clip.width, clip.height);
      g.setColor(oldColor);
    }

    @Override
    protected void paintChildren(Graphics g) {
      updateText();
      super.paintChildren(g);
    }

    @Override
    public void dispose() {
      EditorFactory.getInstance().releaseEditor(myEditor);
    }

    private void updateText() {
      FontMetrics fontMetrics = ((EditorImpl)myEditor).getFontMetrics(Font.PLAIN);
      DocumentEx document = myEditor.getDocument();
      Insets insets = getInsets();
      int maxLineWidth = getWidth() - (insets != null ? insets.left + insets.right : 0);

      myDocumentTextBuilder.setLength(0);
      if (getHeight() / myEditor.getLineHeight() < 1.1f) {
        String line = document.getText().replace('\n', RETURN_SYMBOL);
        appendAbbreviatedLine(myDocumentTextBuilder, line, fontMetrics, maxLineWidth);
      }
      else {
        for (LineIterator line = document.createLineIterator(); !line.atEnd(); line.advance()) {
          String lineText = document.getText(new TextRange(line.getStart(), line.getEnd()));
          appendAbbreviatedLine(myDocumentTextBuilder, lineText, fontMetrics, maxLineWidth);
        }
      }

      setText(myDocumentTextBuilder.toString(), true);
    }

    private static void appendAbbreviatedLine(StringBuilder to, String line, FontMetrics metrics, int maxWidth) {
      if (metrics.charWidth('m') * line.length() <= maxWidth) {
        to.append(line);
        return;
      }

      int abbrWidth = metrics.stringWidth(ABBREVIATION_SUFFIX);
      int abbrIdx = 0;

      for (; abbrIdx < line.length(); abbrIdx++) {
        int nextCharWidth = metrics.charWidth(line.charAt(abbrIdx));
        if (abbrWidth + nextCharWidth >= maxWidth) break;
        abbrWidth += nextCharWidth;
      }

      to.append(line, 0, abbrIdx);
      to.append(ABBREVIATION_SUFFIX);
      if (abbrIdx != line.length() && line.endsWith("\n")) {
        to.append('\n');
      }
    }

    private void setText(String text, boolean abbreviationOfCurrentText) {
      if (!abbreviationOfCurrentText) {
        myEditor.getMarkupModel().removeAllHighlighters();
      }

      myEditor.getDocument().setText(text);
      myEditor.getHighlighter().setText(text);
      ((EditorImpl)myEditor).resetSizes();

      SelectionModel selectionModel = myEditor.getSelectionModel();
      selectionModel.setSelection(0, selectionModel.hasSelection() ? myEditor.getDocument().getTextLength() : 0);

      if (!abbreviationOfCurrentText) {
        myPreferredSize = super.getPreferredSize();
      }
    }
  }

  private static class MyDocument extends UserDataHolderBase implements DocumentEx {

    RangeMarkerTree<RangeMarkerEx> myRangeMarkers = new RangeMarkerTree<RangeMarkerEx>(this) {};
    LineSet myLineSet = new LineSet();

    char[] myChars = ArrayUtil.EMPTY_CHAR_ARRAY;
    String myString = "";

    @Override
    public void setText(@NotNull CharSequence text) {
      String s = StringUtil.convertLineSeparators(text.toString());
      myChars = new char[s.length()];
      s.getChars(0, s.length(), myChars, 0);
      myString = new String(myChars);
      myLineSet.documentCreated(this);
    }

    @Override
    public void setStripTrailingSpacesEnabled(boolean isEnabled) {
    }

    @NotNull
    @Override
    public LineIterator createLineIterator() {
      return myLineSet.createIterator();
    }

    @Override public void setModificationStamp(long modificationStamp) { }
    @Override public void addEditReadOnlyListener(@NotNull EditReadOnlyListener listener) { }
    @Override public void removeEditReadOnlyListener(@NotNull EditReadOnlyListener listener) { }
    @Override public void replaceText(@NotNull CharSequence chars, long newModificationStamp) { }
    @Override public void moveText(int srcStart, int srcEnd, int dstOffset) { }
    @Override public int getListenersCount() { return 0; }
    @Override public void suppressGuardedExceptions() { }
    @Override public void unSuppressGuardedExceptions() { }
    @Override public boolean isInEventsHandling() { return false; }
    @Override public void clearLineModificationFlags() { }
    @Override public boolean removeRangeMarker(@NotNull RangeMarkerEx rangeMarker) { return myRangeMarkers.removeInterval(rangeMarker); }

    @Override
    public void registerRangeMarker(@NotNull RangeMarkerEx rangeMarker,
                                    int start,
                                    int end,
                                    boolean greedyToLeft,
                                    boolean greedyToRight,
                                    int layer) {
      myRangeMarkers.addInterval(rangeMarker, start, end, greedyToLeft, greedyToRight, layer);
    }

    @Override public boolean isInBulkUpdate() { return false; }
    @Override public void setInBulkUpdate(boolean value) { }
    @NotNull @Override public List<RangeMarker> getGuardedBlocks() { return Collections.emptyList(); }
    @Override public boolean processRangeMarkers(@NotNull Processor<RangeMarker> processor) { return myRangeMarkers.process(processor); }
    @Override public boolean processRangeMarkersOverlappingWith(int start, int end, @NotNull Processor<RangeMarker> processor) { return myRangeMarkers.processOverlappingWith(start, end, processor); }
    @NotNull
    @Override public String getText() { return myString; }
    @NotNull @Override public String getText(@NotNull TextRange range) { return range.substring(getText()); }
    @NotNull @Override public CharSequence getCharsSequence() { return myString; }
    @NotNull @Override public CharSequence getImmutableCharSequence() { return getText(); }
    @NotNull @Override public char[] getChars() { return myChars; }
    @Override public int getTextLength() { return myChars.length; }
    @Override public int getLineCount() { return myLineSet.findLineIndex(myChars.length) + 1; }
    @Override public int getLineNumber(int offset) { return myLineSet.findLineIndex(offset); }
    @Override public int getLineStartOffset(int line) { return myChars.length == 0 ? 0 : myLineSet.getLineStart(line); }
    @Override public int getLineEndOffset(int line) { return myChars.length == 0? 0 : myLineSet.getLineEnd(line); }
    @Override public void insertString(int offset, @NotNull CharSequence s) { }
    @Override public void deleteString(int startOffset, int endOffset) { }
    @Override public void replaceString(int startOffset, int endOffset, @NotNull CharSequence s) { }
    @Override public boolean isWritable() { return false; }
    @Override public long getModificationStamp() { return 0; }
    @Override public void fireReadOnlyModificationAttempt() { }
    @Override public void addDocumentListener(@NotNull DocumentListener listener) { }
    @Override public void addDocumentListener(@NotNull DocumentListener listener, @NotNull Disposable parentDisposable) { }
    @Override public void removeDocumentListener(@NotNull DocumentListener listener) { }
    @NotNull @Override public RangeMarker createRangeMarker(int startOffset, int endOffset) { return null; }
    @NotNull @Override public RangeMarker createRangeMarker(int startOffset, int endOffset, boolean surviveOnExternalChange) { return null; }
    @Override public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) { }
    @Override public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) { }
    @Override public void setReadOnly(boolean isReadOnly) { }
    @NotNull @Override public RangeMarker createGuardedBlock(int startOffset, int endOffset) { return null; }
    @Override public void removeGuardedBlock(@NotNull RangeMarker block) { }
    @Nullable @Override public RangeMarker getOffsetGuard(int offset) { return null; }
    @Nullable @Override public RangeMarker getRangeGuard(int start, int end) { return null; }
    @Override public void startGuardedBlockChecking() { }
    @Override public void stopGuardedBlockChecking() { }
    @Override public void setCyclicBufferSize(int bufferSize) { }

    @NotNull @Override public RangeMarker createRangeMarker(@NotNull TextRange textRange) { return null; }
    @Override public int getLineSeparatorLength(int line) { return 0; }
  }

}
