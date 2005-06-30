package com.intellij.psi.formatter.java;

import com.intellij.lang.ASTNode;
import com.intellij.newCodeFormatting.Alignment;
import com.intellij.newCodeFormatting.Indent;
import com.intellij.newCodeFormatting.Wrap;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CodeStyleSettings;

/**
 * @author max
 */
public class PartialWhitespaceBlock extends SimpleJavaBlock {
  private TextRange myRange;

  public PartialWhitespaceBlock(final ASTNode node,
                                final TextRange range,
                                final Wrap wrap,
                                final Alignment alignment,
                                final Indent indent,
                                CodeStyleSettings settings) {
    super(node, wrap, alignment, indent, settings);
    myRange = range;
  }

  @Override
  public TextRange getTextRange() {
    return myRange;
  }
}
