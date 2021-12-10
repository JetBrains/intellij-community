package org.intellij.plugins.markdown.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.DelegatingItemPresentation;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElementVisitor;
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor;
import org.jetbrains.annotations.NotNull;

public class MarkdownBlockQuote extends MarkdownCompositePsiElementBase {
  public MarkdownBlockQuote(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof MarkdownElementVisitor) {
      ((MarkdownElementVisitor)visitor).visitBlockQuote(this);
      return;
    }

    super.accept(visitor);
  }

  @Override
  public String getPresentableTagName() {
    return "blockquote";
  }

  @Override
  public ItemPresentation getPresentation() {
    return new DelegatingItemPresentation(super.getPresentation()) {
      @Override
      public String getLocationString() {
        if (!isValid()) {
          return null;
        }
        if (hasTrivialChildren()) {
          return super.getLocationString();
        }
        else {
          return null;
        }
      }
    };
  }
}
