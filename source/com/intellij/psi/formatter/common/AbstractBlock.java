package com.intellij.psi.formatter.common;

import com.intellij.codeFormatting.general.FormatterUtil;
import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.impl.source.codeStyle.Helper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class AbstractBlock implements Block {
  protected final ASTNode myNode;
  private List<Block> mySubBlocks;
  protected Wrap myWrap;
  protected Alignment myAlignment;

  private boolean myIsInsideBuilding = false;

  protected AbstractBlock(final ASTNode node, final Wrap wrap, final Alignment alignment) {
    myNode = node;
    myWrap = wrap;
    myAlignment = alignment;
  }

  @NotNull
  public TextRange getTextRange() {
    return myNode.getTextRange();
  }

  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.formatter.common.AbstractBlock");

  @NotNull
  public List<Block> getSubBlocks() {
    if (mySubBlocks == null) {
      LOG.assertTrue(!myIsInsideBuilding);
      myIsInsideBuilding = true;
      try {
        mySubBlocks = buildChildren();
      }
      finally {
        myIsInsideBuilding = false;
      }
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

  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    return new ChildAttributes(getChildIndent(), getFirstChildAlignment());
  }

  private Alignment getFirstChildAlignment() {
    List<Block> subBlocks = getSubBlocks();
    for (final Block subBlock : subBlocks) {
      Alignment alignment = subBlock.getAlignment();
      if (alignment != null) {
        return alignment;
      }
    }
    return null;
  }

  @Nullable
  protected Indent getChildIndent() {
    return null;
  }

  public boolean isIncomplete() {
    return FormatterUtil.isIncompleted(getTreeNode());
  }

  public boolean isLeaf() {
    return Helper.mayShiftIndentInside(myNode);
  }
}
