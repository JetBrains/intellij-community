package com.intellij.formatting;

import com.intellij.formatting.Alignment;

import java.util.List;
import java.util.ArrayList;
import java.util.ListIterator;

class AlignmentImpl extends Alignment {
  private List<LeafBlockWrapper> myOffsetRespBlocks = new ArrayList<LeafBlockWrapper>();
  private final long myId;
  private static long ourId = 0;

  public String getId() {
    return String.valueOf(myId);
  }

  public void reset() {
    myOffsetRespBlocks.clear();
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
    if (blockAfter != null) {
      for (ListIterator<LeafBlockWrapper> each = myOffsetRespBlocks.listIterator(myOffsetRespBlocks.size()); each.hasPrevious();) {
        final LeafBlockWrapper current = each.previous();
        if (current.getStartOffset() < blockAfter.getStartOffset()) break;
        each.remove();
      }
    }
    return myOffsetRespBlocks.isEmpty() ? null : myOffsetRespBlocks.get(myOffsetRespBlocks.size() - 1);
  }

  void setOffsetRespBlock(final LeafBlockWrapper block) {
    myOffsetRespBlocks.add(block);
  }

}
