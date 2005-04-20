package com.intellij.psi.formatter.newXmlFormatter.xml;

import com.intellij.lang.ASTNode;
import com.intellij.newCodeFormatting.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.codeFormatting.general.FormatterUtil;

import java.util.List;

public abstract class AbstractBlock implements Block {
  protected final ASTNode myNode;
  private List<Block> mySubBlocks;
  protected final Wrap myWrap;
  protected final Alignment myAlignment;

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

  protected boolean containsWhiteSpacesOnly(final ASTNode node) {
    if (node.getElementType() == ElementType.WHITE_SPACE) return true;
    if (node.getElementType() == ElementType.DOC_COMMENT_DATA && node.textContains('\n') && node.getText().trim().length() == 0) {
      return true;
      //EnterActionTest && JavaDocParamTest
    }
    if (node instanceof LeafElement) return false;
    ChameleonTransforming.transformChildren(node);
    ASTNode child = node.getFirstChildNode();
    while (child != null) {
      if (!containsWhiteSpacesOnly(child)) return false;
      child = child.getTreeNext();
    }
    return true;
  }

  public ASTNode getTreeNode() {
    return myNode;
  }

  public ChildAttributes getChildAttributes(final int newChildIndex) {
    return new ChildAttributes(getIndent(), null);
  }

  public boolean isIncopleted() {
    return FormatterUtil.isIncompleted(getTreeNode());
  }
}
