package com.intellij.psi.formatter.common;

import com.intellij.codeFormatting.general.FormatterUtil;
import com.intellij.lang.ASTNode;
import com.intellij.newCodeFormatting.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.LeafElement;

import java.util.List;

public abstract class AbstractBlock implements Block {
  protected final ASTNode myNode;
  private List<Block> mySubBlocks;
  protected Wrap myWrap;
  protected Alignment myAlignment;

  protected AbstractBlock(final ASTNode node, final Wrap wrap, final Alignment alignment) {
    myNode = node;
    myWrap = wrap;
    myAlignment = alignment;
  }

  public TextRange getTextRange() {
    return myNode.getTextRange();
  }

  public List<Block> getSubBlocks() {
    if (mySubBlocks == null) {
      mySubBlocks = buildChildren();
    }
    return mySubBlocks;
  }

  protected abstract List<Block> buildChildren();

  public Wrap getWrap() {
    return myWrap;
  }

  public Indent getIndent() {
    return null;
  }

  public Alignment getAlignment() {
    return myAlignment;
  }

  public ASTNode getNode() {
    return myNode;
  }

  public ASTNode getTreeNode() {
    return myNode;
  }

  public ChildAttributes getChildAttributes(final int newChildIndex) {
    return new ChildAttributes(getChildIndent(), null);
  }

  protected Indent getChildIndent() {
    return null;
  }

  public boolean isIncomplete() {
    return FormatterUtil.isIncompleted(getTreeNode());
  }
}
