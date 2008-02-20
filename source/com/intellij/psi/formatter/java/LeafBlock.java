package com.intellij.psi.formatter.java;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.formatter.common.JavaBlockUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class LeafBlock implements Block{
  private int myStartOffset = -1;
  private final ASTNode myNode;
  private final Wrap myWrap;
  private final Alignment myAlignment;

  private static final ArrayList<Block> EMPTY_SUB_BLOCKS = new ArrayList<Block>();
  private final Indent myIndent;

  public LeafBlock(final ASTNode node,
                   final Wrap wrap,
                   final Alignment alignment,
                   Indent indent) {
    myNode = node;
    myWrap = wrap;
    myAlignment = alignment;
    myIndent = indent;
  }


  @NotNull
  public TextRange getTextRange() {
    if (myStartOffset != -1) {
      return new TextRange(myStartOffset, myStartOffset + myNode.getTextLength());
    }
    return myNode.getTextRange();
  }

  @NotNull
  public List<Block> getSubBlocks() {
    return EMPTY_SUB_BLOCKS;
  }

  public Wrap getWrap() {
    return myWrap;
  }

  public Indent getIndent() {
    return myIndent;
  }

  public Alignment getAlignment() {
    return myAlignment;
  }

  public Spacing getSpacing(Block child1, Block child2) {
    return null;
  }

  public ASTNode getTreeNode() {
    return myNode;
  }

  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    return new ChildAttributes(getIndent(), null);
  }

  public boolean isIncomplete() {
    return false;
  }

  public boolean isLeaf() {
    return JavaBlockUtil.mayShiftIndentInside(myNode);
  }

  public void setStartOffset(final int startOffset) {
    myStartOffset = startOffset;
   // if (startOffset != -1) assert startOffset == myNode.getTextRange().getStartOffset();
  }
}
