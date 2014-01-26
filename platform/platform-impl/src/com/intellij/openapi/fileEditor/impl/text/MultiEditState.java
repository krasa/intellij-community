package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.util.Range;

import java.util.Collection;
import java.util.List;

/**
 * @author Vojtech Krasa
 */
public class MultiEditState {
  private final List<Range<Integer>> myMultiEditSelections;
  private final Collection<Integer> myMultiCaretOffsets;

  public MultiEditState( List<Range<Integer>> selections, Collection<Integer> multiCaretOffsets) {
    myMultiEditSelections = selections;
    myMultiCaretOffsets = multiCaretOffsets;
  }

  public List<Range<Integer>> getMultiEditSelections() {
    return myMultiEditSelections;
  }

  public Collection<Integer> getMultiCaretOffsets() {
    return myMultiCaretOffsets;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MultiEditState that = (MultiEditState)o;

    if (myMultiCaretOffsets != null ? !myMultiCaretOffsets.equals(that.myMultiCaretOffsets) : that.myMultiCaretOffsets != null)
      return false;
    if (myMultiEditSelections != null ? !myMultiEditSelections.equals(that.myMultiEditSelections) : that.myMultiEditSelections != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myMultiEditSelections != null ? myMultiEditSelections.hashCode() : 0;
    result = 31 * result + (myMultiCaretOffsets != null ? myMultiCaretOffsets.hashCode() : 0);
    return result;
  }
}
