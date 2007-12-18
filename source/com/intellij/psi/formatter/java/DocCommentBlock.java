package com.intellij.psi.formatter.java;

import com.intellij.lang.ASTNode;
import com.intellij.formatting.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.formatter.FormatterUtil;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

public class DocCommentBlock extends AbstractJavaBlock{
  public DocCommentBlock(final ASTNode node, final Wrap wrap, final Alignment alignment, final Indent indent, CodeStyleSettings settings) {
    super(node, wrap, alignment, indent, settings);
  }

  protected List<Block> buildChildren() {
    ChameleonTransforming.transformChildren(myNode);

    final ArrayList<Block> result = new ArrayList<Block>();

    ASTNode child = myNode.getFirstChildNode();
    while (child != null) {
      if (child.getElementType() == ElementType.DOC_COMMENT_START) {
        result.add(createJavaBlock(child, mySettings, Indent.getNoneIndent(),
                                   null, null));
      } else if (!FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0){
        result.add(createJavaBlock(child, mySettings, Indent.getSpaceIndent(1), null, null));
      }
      child = child.getTreeNext();
    }
    return result;

  }

  protected Wrap getReservedWrap() {
    return null;
  }

  protected void setReservedWrap(final Wrap reservedWrap) {
  }

  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    return new ChildAttributes(Indent.getSpaceIndent(1), null);
  }
}
