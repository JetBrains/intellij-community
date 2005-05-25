package com.intellij.psi.formatter.java;

import com.intellij.newCodeFormatting.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.lang.ASTNode;

import java.util.List;
import java.util.ArrayList;

public class LeafBlock implements Block{

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

  public TextRange getTextRange() {
    return myNode.getTextRange();
  }

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

  public SpaceProperty getSpaceProperty(Block child1, Block child2) {
    return null;
  }

  public ASTNode getTreeNode() {
    return myNode;
  }

  public ChildAttributes getChildAttributes(final int newChildIndex) {
    return new ChildAttributes(getIndent(), null);
  }

  public boolean isIncomplete() {
    return false;
  }
}
