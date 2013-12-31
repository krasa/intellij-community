package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.util.Range;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author Vojtech Krasa
 */
public class MultiEditState {
  @NotNull
  private final List<Range<Integer>> myMultiEditRanges;

  public MultiEditState(@NotNull List<Range<Integer>> multiEditRanges, Collection<Integer> multiCaretOffsets) {
    for (Integer caretOffset : multiCaretOffsets) {
      multiEditRanges.add(new Range<Integer>(caretOffset, caretOffset));
    }
    myMultiEditRanges = multiEditRanges;
  }

  @NotNull
  public List<Range<Integer>> getMultiEditRanges() {
    return myMultiEditRanges;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MultiEditState that = (MultiEditState)o;

    if (!myMultiEditRanges.equals(that.myMultiEditRanges)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myMultiEditRanges.hashCode();
  }
}
