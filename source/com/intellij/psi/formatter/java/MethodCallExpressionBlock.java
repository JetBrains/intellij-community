package com.intellij.psi.formatter.java;

import com.intellij.lang.ASTNode;
import com.intellij.newCodeFormatting.Wrap;
import com.intellij.newCodeFormatting.Alignment;
import com.intellij.newCodeFormatting.Indent;
import com.intellij.newCodeFormatting.Block;
import com.intellij.psi.codeStyle.CodeStyleSettings;

import java.util.List;

public class MethodCallExpressionBlock extends AbstractJavaBlock{
  public MethodCallExpressionBlock(final ASTNode node, final Wrap wrap, final Alignment alignment, final Indent indent, final CodeStyleSettings settings) {
    super(node, wrap, alignment, indent, settings);
  }

  protected List<Block> buildChildren() {
    return null;
  }

  protected Wrap getReservedWrap() {
    return null;
  }

  protected void setReservedWrap(final Wrap reservedWrap) {
  }
}
