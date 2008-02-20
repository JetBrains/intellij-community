package com.intellij.psi.formatter.common;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.formatter.FormatterUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AbstractBlock implements Block {
  public static final List<Block> EMPTY = Collections.unmodifiableList(new ArrayList<Block>(0));
  protected final ASTNode myNode;
  private List<Block> mySubBlocks;
  protected final Wrap myWrap;
  protected final Alignment myAlignment;

  protected AbstractBlock(final ASTNode node, final Wrap wrap, final Alignment alignment) {
    myNode = node;
    myWrap = wrap;
    myAlignment = alignment;
  }

  @NotNull
  public TextRange getTextRange() {
    return myNode.getTextRange();
  }

  @NotNull
  public List<Block> getSubBlocks() {
    if (mySubBlocks == null) {

      final List<Block> list = buildChildren();
      mySubBlocks = list.size() > 0 ? list:EMPTY;
    }
    return mySubBlocks;
  }

  protected abstract List<Block> buildChildren();

  public final Wrap getWrap() {
    return myWrap;
  }

  public Indent getIndent() {
    return null;
  }

  public final Alignment getAlignment() {
    return myAlignment;
  }

  public final ASTNode getNode() {
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
    return FormatterUtil.isIncompleted(getNode());
  }

  public boolean isLeaf() {
    return JavaBlockUtil.mayShiftIndentInside(myNode);
  }
}
