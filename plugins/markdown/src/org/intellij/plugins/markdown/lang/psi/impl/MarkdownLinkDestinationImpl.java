package org.intellij.plugins.markdown.lang.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Please use {@link MarkdownLinkDestination} instead.
 */
@Deprecated
public class MarkdownLinkDestinationImpl extends MarkdownLinkDestination {
  public MarkdownLinkDestinationImpl(@NotNull ASTNode node) {
    super(node);
  }
}
