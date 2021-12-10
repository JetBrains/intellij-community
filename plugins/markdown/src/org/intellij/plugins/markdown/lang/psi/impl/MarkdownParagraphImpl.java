package org.intellij.plugins.markdown.lang.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Please use {@link MarkdownParagraph} instead.
 */
@Deprecated
public class MarkdownParagraphImpl extends MarkdownParagraph {
  public MarkdownParagraphImpl(@NotNull ASTNode node) {
    super(node);
  }
}
