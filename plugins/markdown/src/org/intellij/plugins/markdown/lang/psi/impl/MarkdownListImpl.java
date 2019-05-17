package org.intellij.plugins.markdown.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor;
import org.jetbrains.annotations.NotNull;

public class MarkdownListImpl extends MarkdownCompositePsiElementBase {
  public MarkdownListImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof MarkdownElementVisitor) {
      ((MarkdownElementVisitor)visitor).visitList(this);
      return;
    }

    super.accept(visitor);
  }


  @Override
  public String getPresentableTagName() {
    return getNode().getElementType() == MarkdownElementTypes.ORDERED_LIST
           ? "ol"
           : "ul";
  }
}
