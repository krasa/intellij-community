package com.intellij.openapi.editor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.actionSystem.MultiEditListener;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Condition;
import com.intellij.util.CommonProcessors;
import com.intellij.util.EventDispatcher;
import com.intellij.util.FilteringProcessor;
import com.intellij.util.containers.HashSet;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class MultiCaretModelImpl implements CaretModel, PrioritizedDocumentListener, Disposable, MultiEditListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.MultiCaretModelImpl");

  private final EditorImpl myEditor;
  private List<CaretModelImpl> carets;
  private CaretModelImpl activeCaret;

  private final EventDispatcher<CaretListener> myCaretListeners = EventDispatcher.create(CaretListener.class);
  private boolean myIsInUpdate;

  private ReentrantLock myMultiEditLock = new ReentrantLock();
  private CaretEvent myBlockedCaretEvent;
  private boolean isDocumentChanged;


  public MultiCaretModelImpl(EditorImpl editor) {
    myEditor = editor;
    CaretModelImpl caretModel = new CaretModelImpl(editor, this);
    activeCaret = caretModel;
    carets = new ArrayList<CaretModelImpl>();
    carets.add(caretModel);
  }

  @Override
  public void addCaretListener(@NotNull CaretListener listener) {
    getCaretListeners().addListener(listener);
  }

  @Override
  public void removeCaretListener(@NotNull CaretListener listener) {
    getCaretListeners().removeListener(listener);
  }


  @Override
  public void moveToVisualPosition(@NotNull VisualPosition pos) {
    activeCaret.moveToVisualPosition(pos);
  }

  @Override
  public void moveToOffset(int offset) {
    activeCaret.moveToOffset(offset);
  }

  @Override
  public void moveToOffset(int offset, boolean locateBeforeSoftWrap) {
    activeCaret.moveToOffset(offset, locateBeforeSoftWrap);
  }

  public void setIgnoreWrongMoves(boolean ignoreWrongMoves) {
    activeCaret.setIgnoreWrongMoves(ignoreWrongMoves);
  }

  @Override
  public void moveCaretRelatively(int columnShift, int lineShift, boolean withSelection, boolean blockSelection, boolean scrollToCaret) {
    activeCaret.moveCaretRelatively(columnShift, lineShift, withSelection, blockSelection, scrollToCaret);
  }

  @Override
  public void moveToLogicalPosition(@NotNull LogicalPosition pos) {
    activeCaret.moveToLogicalPosition(pos);
  }

  @Override
  public boolean isUpToDate() {
    return activeCaret.isUpToDate();
  }

  @Override
  @NotNull
  public LogicalPosition getLogicalPosition() {
    return activeCaret.getLogicalPosition();
  }

  @Override
  @NotNull
  public VisualPosition getVisualPosition() {
    return activeCaret.getVisualPosition();
  }

  @Override
  public int getOffset() {
    return activeCaret.getOffset();
  }

  @Override
  public int getVisualLineStart() {
    return activeCaret.getVisualLineStart();
  }

  @Override
  public int getVisualLineEnd() {
    return activeCaret.getVisualLineEnd();
  }

  @Override
  public TextAttributes getTextAttributes() {
    return activeCaret.getTextAttributes();
  }

  public void reinitSettings() {
    activeCaret.reinitSettings();
  }


  @Override
  public void beforeDocumentChange(DocumentEvent e) {
    myIsInUpdate = true;
  }

  @Override
  public int getPriority() {
    return EditorDocumentPriorities.CARET_MODEL;
  }

  @Override
  public void documentChanged(DocumentEvent e) {
    isDocumentChanged = true;
    try {
      for (CaretModelImpl caret : carets) {
        caret.updateCaretPosition((DocumentEventImpl)e);
      }
    }
    finally {
      isDocumentChanged = false;
    }
  }

  protected boolean isDocumentChanged() {
    return isDocumentChanged;
  }


  @Override
  public void dispose() {
    for (CaretModelImpl caret : carets) {
      caret.dispose();
    }
  }

  public void updateVisualPosition() {
    for (CaretModelImpl caret : carets) {
      caret.updateVisualPosition();
    }
  }

  @Override
  public void removeMultiCarets() {
    if (!hasMultiCarets()) {
      return;
    }
    carets.clear();
    carets.add(activeCaret);
    myEditor.caretsChanged();
  }


  @Override
  public boolean hasMultiCarets() {
    return carets.size() > 1;
  }


  @Override
  public CaretModel addMultiCaret(int offset) {
    if (myEditor.getDocument().getTextLength() < offset) {
      return null;
    }
    final RangeHighlighterEx selection = getOverlappingHighlighter(offset, offset, HighlighterLayer.MULTI_EDIT_SELECTION);
    if (selection == null) {
      return createCaret(offset);
    }
    else {
      final CaretModelImpl selectionCaret = getOverlappingCaret(selection.getStartOffset(), selection.getEndOffset());

      if (selection.getStartOffset() == offset || selection.getEndOffset() == offset) {
        if (selectionCaret != null && selectionCaret.getOffset() != offset) {
          selectionCaret.moveToOffset(offset);
        }
        else if (selectionCaret == null) {
          return createCaret(offset);
        }
      }
      else if (selectionCaret != null) {
        removeCaret(activeCaret);
        setActiveCaret(selectionCaret);
      }
    }
    return null;
  }

  private CaretModelImpl getOverlappingCaret(int startOffset, int endOffset) {
    for (CaretModelImpl caret : carets) {
      int offset = caret.getOffset();
      if (offset >= startOffset && offset <= endOffset) {
        return caret;
      }
    }
    return null;
  }


  private CaretModelImpl createCaret(int offset) {
    CaretModelImpl e = new CaretModelImpl(myEditor, this);
    e.moveToOffset(offset);
    carets.add(e);
    return e;
  }

  private RangeHighlighterEx getOverlappingHighlighter(final int start, final int end, final int highlighterLayer) {
    final Condition<RangeHighlighterEx> condition = new Condition<RangeHighlighterEx>() {

      @Override
      public boolean value(RangeHighlighterEx rangeHighlighterEx) {
        return rangeHighlighterEx.getLayer() == highlighterLayer;
      }
    };
    final CommonProcessors.FindFirstProcessor<RangeHighlighterEx> findFirstProcessor =
      new CommonProcessors.FindFirstProcessor<RangeHighlighterEx>();
    final FilteringProcessor<RangeHighlighterEx> filteringProcessor =
      new FilteringProcessor<RangeHighlighterEx>(condition, findFirstProcessor);
    final EditorMarkupModelImpl markupModel = (EditorMarkupModelImpl)myEditor.getMarkupModel();
    markupModel.processRangeHighlightersOverlappingWith(start, end, filteringProcessor);
    return findFirstProcessor.getFoundValue();
  }

  @Override
  public Collection<Integer> getMultiCaretOffsets() {
    if (!hasMultiCarets()) {
      return Collections.emptyList();
    }
    sweep();
    Set<Integer> offsets = new HashSet<Integer>();
    for (CaretModelImpl caret : carets) {
      offsets.add(caret.getOffset());
    }

    return offsets;
  }

  @Override
  public Collection<Integer> getAndRemoveMultiCaretOffsets() {
    Collection<Integer> multiCaretOffsets = getMultiCaretOffsets();
    removeMultiCarets();
    return multiCaretOffsets;
  }

  EventDispatcher<CaretListener> getCaretListeners() {
    return myCaretListeners;
  }

  @Override
  public List<CaretModel> getMultiCarets() {
    sweep();
    return new ArrayList<CaretModel>(carets);
  }

  private void sweep() {
    //TODO krasa optimize this shit
    if (carets.size() > 1) {
      TIntHashSet tIntHashSet = new TIntHashSet();
      List<CaretModel> delete = new ArrayList<CaretModel>();
      for (CaretModelImpl caret : carets) {
        if (tIntHashSet.contains(caret.getOffset())) {
          delete.add(caret);
        }
        else {
          tIntHashSet.add(caret.getOffset());
        }
      }
      if (!delete.isEmpty()) {
        LOG.debug("sweep - removing " + delete.size());
        for (CaretModel caretModel : delete) {
          final CaretModelImpl caretImpl = (CaretModelImpl)caretModel;
          carets.remove(caretImpl);
          caretImpl.dispose();
          if (activeCaret == caretModel) {
            activeCaret = carets.get(0);
          }
        }
        myEditor.caretRemoved(null);
      }
    }
  }

  @Override
  public void setActiveCaret(CaretModel caretModel) {
    if (!(caretModel instanceof CaretModelImpl)) {
      throw new IllegalArgumentException("caretModel must be instance of CaretModelImpl");
    }
    if (!carets.contains(caretModel)) {
      throw new IllegalArgumentException("Caret was already removed");
    }

    activeCaret = (CaretModelImpl)caretModel;
    myEditor.setLastColumnNumber(activeCaret.getLogicalPosition().column);
  }

  void validateCallContext() {
    LOG.assertTrue(!myIsInUpdate, "Caret model is in its update process. All requests are illegal at this point.");
  }

  protected boolean isInUpdate() {
    return myIsInUpdate;
  }

  void finishUpdate() {
    myIsInUpdate = false;
  }

  void caretPositionChanged(CaretEvent event) {
    if (isMultiEditLocked()) {
      myBlockedCaretEvent = event;
    }
    else {
      getCaretListeners().getMulticaster().caretPositionChanged(event);
    }
  }

  @Override
  public void beforeMultiCaretsExecution() {
    myMultiEditLock.lock();
  }

  @Override
  public void afterMultiCaretsExecution() {
    myMultiEditLock.unlock();
    if (!isMultiEditLocked() && myBlockedCaretEvent != null) {
      getCaretListeners().getMulticaster().caretPositionChanged(myBlockedCaretEvent);
      myBlockedCaretEvent = null;
    }
  }

  public boolean isMultiEditLocked() {
    return myMultiEditLock.isLocked();
  }

  public void removeCaret(CaretModel multiCaret) {
    if (carets.size() == 1) {
      throw new IllegalStateException("trying to remove the last caret");
    }

    final CaretModelImpl caretImpl = (CaretModelImpl)multiCaret;
    carets.remove(caretImpl);
    caretImpl.dispose();

    myEditor.caretRemoved(multiCaret);
    if (activeCaret == multiCaret) {
      activeCaret = carets.get(0);
    }
  }
}
