package com.intellij.psi.formatter.java;

import com.intellij.lang.ASTNode;
import com.intellij.formatting.Wrap;
import com.intellij.formatting.Alignment;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Block;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.tree.IElementType;

import java.util.List;

public class MethodCallExpressionBlock extends AbstractJavaBlock{
  public MethodCallExpressionBlock(final ASTNode node, final Wrap wrap, final Alignment alignment, final Indent indent, final CodeStyleSettings settings) {
    super(node, wrap, alignment, indent, settings);
  }

  protected List<Block> buildChildren() {
    return null;
  }

  protected Wrap getReservedWrap(final IElementType elementType) {
    return null;
  }

  protected void setReservedWrap(final Wrap reservedWrap, final IElementType operationType) {
  }
}
