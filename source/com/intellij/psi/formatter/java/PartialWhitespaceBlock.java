package com.intellij.psi.formatter.java;

import com.intellij.lang.ASTNode;
import com.intellij.formatting.Alignment;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Wrap;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class PartialWhitespaceBlock extends SimpleJavaBlock {
  private final TextRange myRange;

  public PartialWhitespaceBlock(final ASTNode node,
                                final TextRange range,
                                final Wrap wrap,
                                final Alignment alignment,
                                final Indent indent,
                                CodeStyleSettings settings) {
    super(node, wrap, alignment, indent, settings);
    myRange = range;
  }

  @NotNull
  @Override
  public TextRange getTextRange() {
    return myRange;
  }
}
