package org.intellij.plugins.markdown.lang.psi.impl;

import com.intellij.lang.ASTNode;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.jetbrains.annotations.NotNull;

public class MarkdownTableRowImpl extends MarkdownCompositePsiElementBase {
  public MarkdownTableRowImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public String getPresentableTagName() {
    if (getNode().getElementType() == MarkdownElementTypes.TABLE_HEADER) {
      return "th";
    }
    else {
      return "tr";
    }
  }
}
