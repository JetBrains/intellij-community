package org.intellij.plugins.markdown.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor;
import org.jetbrains.annotations.NotNull;

public class MarkdownTableImpl extends MarkdownCompositePsiElementBase {
  public MarkdownTableImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof MarkdownElementVisitor) {
      ((MarkdownElementVisitor)visitor).visitTable(this);
      return;
    }

    super.accept(visitor);
  }

  @Override
  public String getPresentableTagName() {
    return "table";
  }
}
