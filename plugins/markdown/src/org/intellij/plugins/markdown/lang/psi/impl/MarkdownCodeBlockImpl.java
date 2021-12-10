package org.intellij.plugins.markdown.lang.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Please use {@link MarkdownCodeBlock} instead.
 */
@Deprecated
public class MarkdownCodeBlockImpl extends MarkdownCodeBlock {
  public MarkdownCodeBlockImpl(@NotNull ASTNode node) {
    super(node);
  }
}
