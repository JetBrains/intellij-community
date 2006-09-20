package com.intellij.openapi.editor.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.editor.ex.HighlighterIterator;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import gnu.trove.Equality;

class RangeIterator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.RangeIterator");
  private static final int NO_NEXT = -1;
  private int myRangeEnd;

  public interface Gaps {
    boolean isGapAt(int offset);
  }

  private final HighlighterIterator mySource;
  private final Equality<TextAttributes> myEquality;
  private final Gaps myGaps;
  private final Condition<TextAttributes> myFilter;
  private boolean mySourceOutOfRange = false;

  private int myStart;
  private int myEnd;
  private TextAttributes myTextAttributes;
  private int myNextExpanded;

  public RangeIterator(Gaps foldingModel, Equality<TextAttributes> equality,
                       HighlighterIterator source, Condition<TextAttributes> filter) {
    mySource = source;
    myGaps = foldingModel;
    myEquality = equality;
    myFilter = filter;
  }

  public void init(TextRange range) {
    int rangeStart = range.getStartOffset();
    myRangeEnd = range.getEndOffset();
    while(!mySource.atEnd()) {
      boolean sourceBeforeRange = rangeStart > mySource.getEnd();
      if (!sourceBeforeRange) break;
      mySource.advance();
    }
    while (!mySource.atEnd() && !checkOutOfRange()) {
      if (myFilter.value(mySource.getTextAttributes())) break;
      mySource.advance();
    }

    if (mySource.atEnd() || mySourceOutOfRange) myNextExpanded = NO_NEXT;
    else {
      myNextExpanded = findExpanded(mySource.getStart());
      if (myNextExpanded == NO_NEXT) myStart = NO_NEXT;
    }
  }

  private boolean checkOutOfRange() {
    if (mySourceOutOfRange) return true;
    mySourceOutOfRange = mySource.getStart() > myRangeEnd;
    return mySourceOutOfRange;
  }

  private void doAdvanceFrom(int start) {
    myStart = start;
    myEnd = myStart;
    doAdvance();
    if (mySource.atEnd()) myNextExpanded = NO_NEXT;
    else myNextExpanded = findExpanded(Math.max(myEnd, mySource.getStart()));
  }

  private void doAdvance() {
    myStart = findExpanded(myStart);
    myEnd = findFolding(myStart, mySource.getEnd(), true);
    myTextAttributes = mySource.getTextAttributes();
    while (myEnd == mySource.getEnd()) {
      if (!advanceSource()) return;
      if (mySource.getStart() != myEnd) return;
      if (!myEquality.equals(myTextAttributes, mySource.getTextAttributes())) return;
      myEnd = findFolding(myEnd, mySource.getEnd(), true);
    }
  }

  private boolean advanceSource() {
    if (mySource.atEnd()) return false;
    do {
      mySource.advance();
    } while (!(mySource.atEnd() || checkOutOfRange() || myFilter.value(mySource.getTextAttributes())));
    return !mySource.atEnd();
  }

  private int findExpanded(int start) {
    start = findFolding(start, mySource.getEnd(), false);
    while (start == mySource.getEnd()) {
      if (!advanceSource()) return NO_NEXT;
      start = findFolding(mySource.getStart(), mySource.getEnd(), false);
    }
    return start;
  }

  private int findFolding(int start, int end, boolean collapsed) {
    int position = start;
    while (position < end) {
      if (myGaps.isGapAt(position) == collapsed) break;
      else position++;
    }
    return position;
  }

  public int getStart() {
    return myStart;
  }

  public int getEnd() {
    return myEnd;
  }

  public TextAttributes getTextAttributes() {
    LOG.assertTrue(myFilter.value(myTextAttributes));
    return myTextAttributes;
  }

  public void advance() {
    doAdvanceFrom(myNextExpanded);
  }

  public boolean atEnd() {
    return myStart == NO_NEXT || mySourceOutOfRange ||
           (mySource.atEnd() && (myNextExpanded == NO_NEXT ||
                                 myNextExpanded == mySource.getEnd()));
  }

  public static class FoldingGaps implements Gaps {
    private final FoldingModel myFoldingModel;

    public FoldingGaps(FoldingModel foldingModel) {
      myFoldingModel = foldingModel;
    }

    public boolean isGapAt(int offset) {
      return myFoldingModel.isOffsetCollapsed(offset);
    }
  }
}
