package org.intellij.plugins.markdown.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor;
import org.jetbrains.annotations.NotNull;

public class MarkdownParagraphImpl extends MarkdownCompositePsiElementBase {

  public MarkdownParagraphImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof MarkdownElementVisitor) {
      ((MarkdownElementVisitor)visitor).visitParagraph(this);
      return;
    }

    super.accept(visitor);
  }

  @Override
  public String getPresentableTagName() {
    return "p";
  }
}
