package org.intellij.plugins.markdown.lang.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement;
import org.intellij.plugins.markdown.structureView.MarkdownBasePresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MarkdownLinkDefinition extends ASTWrapperPsiElement implements MarkdownPsiElement {
  public MarkdownLinkDefinition(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  public PsiElement getLinkLabel() {
    final PsiElement label = findChildByType(MarkdownElementTypes.LINK_LABEL);
    if (label == null) {
      throw new IllegalStateException("Probably parsing failed. Should have a label");
    }
    return label;
  }

  @NotNull
  public PsiElement getLinkDestination() {
    final PsiElement destination = findChildByType(MarkdownElementTypes.LINK_DESTINATION);
    if (destination == null) {
      throw new IllegalStateException("Probably parsing failed. Should have a destination");
    }
    return destination;
  }

  @Nullable
  public PsiElement getLinkTitle() {
    return findChildByType(MarkdownElementTypes.LINK_TITLE);
  }

  @Override
  public ItemPresentation getPresentation() {
    return new MarkdownBasePresentation() {
      @Nullable
      @Override
      public String getPresentableText() {
        if (!isValid()) {
          return null;
        }

        return "Def: " + getLinkLabel().getText() + " → " + getLinkDestination().getText();
      }

      @Nullable
      @Override
      public String getLocationString() {
        if (!isValid()) {
          return null;
        }

        final PsiElement linkTitle = getLinkTitle();
        if (linkTitle == null) {
          return null;
        }
        else {
          return linkTitle.getText();
        }
      }
    };
  }
}
