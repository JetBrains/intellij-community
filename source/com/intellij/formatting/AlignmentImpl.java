package com.intellij.formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Collections;

class AlignmentImpl extends Alignment {
  private static final List<LeafBlockWrapper> EMPTY = Collections.unmodifiableList(new ArrayList<LeafBlockWrapper>(0));
  private List<LeafBlockWrapper> myOffsetRespBlocks = EMPTY;
  private final int myId;
  private static int ourId = 0;

  public String getId() {
    return String.valueOf(myId);
  }

  public void reset() {
    if (myOffsetRespBlocks != EMPTY) myOffsetRespBlocks.clear();
  }

  static class Type{
    public static final Type FULL = new Type();
    public static final Type NORMAL = new Type();
  }

  private final Type myType;

  public AlignmentImpl(final Type type) {
    myType = type;
    myId = ourId++;
  }

  Type getType() {
    return myType;
  }

  LeafBlockWrapper getOffsetRespBlockBefore(final LeafBlockWrapper blockAfter) {
    if (blockAfter != null && myOffsetRespBlocks != EMPTY) {
      for (ListIterator<LeafBlockWrapper> each = myOffsetRespBlocks.listIterator(myOffsetRespBlocks.size()); each.hasPrevious();) {
        final LeafBlockWrapper current = each.previous();
        if (current.getStartOffset() < blockAfter.getStartOffset()) break;
        each.remove();
      }
    }
    return myOffsetRespBlocks.isEmpty() ? null : myOffsetRespBlocks.get(myOffsetRespBlocks.size() - 1);
  }

  void setOffsetRespBlock(final LeafBlockWrapper block) {
    if (myOffsetRespBlocks == EMPTY) myOffsetRespBlocks = new ArrayList<LeafBlockWrapper>(1);
    myOffsetRespBlocks.add(block);
  }

}
