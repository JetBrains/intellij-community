package com.intellij.psi.formatter.xml;

import com.intellij.formatting.*;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class InjectedLanguageBlockWrapper implements Block {
  final Block myOriginal;
  private final int myOffset;
  private List<Block> myBlocks;

  public InjectedLanguageBlockWrapper(final Block original, final int offset) {
    myOriginal = original;
    myOffset = offset;
  }

  public Indent getIndent() {
    return myOriginal.getIndent();
  }

  @Nullable
  public Alignment getAlignment() {
    return myOriginal.getAlignment();
  }

  @NotNull
  public TextRange getTextRange() {
    final TextRange range = myOriginal.getTextRange();
    return new TextRange(myOffset + range.getStartOffset(), myOffset + range.getEndOffset());
  }

  @NotNull
  public List<Block> getSubBlocks() {
    if (myBlocks == null) {
      myBlocks = buildBlocks(myOriginal, myOffset);
    }
    return myBlocks;
  }

  static List<Block> buildBlocks(Block myOriginal, int myOffset) {
    final List<Block> list = myOriginal.getSubBlocks();
    if (list.size() == 0) return AbstractXmlBlock.EMPTY;
    else {
      final ArrayList<Block> result = new ArrayList<Block>(list.size());
      for(Block b:list) result.add(new InjectedLanguageBlockWrapper(b, myOffset));
      return result;
    }
  }

  @Nullable
  public Wrap getWrap() {
    return myOriginal.getWrap();
  }

  @Nullable public Spacing getSpacing(Block child1, Block child2) {
    if (child1 instanceof InjectedLanguageBlockWrapper) child1 = ((InjectedLanguageBlockWrapper)child1).myOriginal;
    if (child2 instanceof InjectedLanguageBlockWrapper) child2 = ((InjectedLanguageBlockWrapper)child2).myOriginal;
    return myOriginal.getSpacing(child1,  child2);
  }

  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    return myOriginal.getChildAttributes(newChildIndex);
  }

  public boolean isIncomplete() {
    return myOriginal.isIncomplete();
  }

  public boolean isLeaf() {
    return myOriginal.isLeaf();
  }
}