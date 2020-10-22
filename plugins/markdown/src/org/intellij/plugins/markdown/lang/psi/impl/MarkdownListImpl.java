package org.intellij.plugins.markdown.lang.psi.impl;

import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

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

  @Override
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      @Override
      public String getPresentableText() {
        PsiElement parent = getParent();

        if (parent instanceof MarkdownListItemImpl) {
          ItemPresentation itemPresentation = parent.getNode().getPsi(MarkdownListItemImpl.class).getPresentation();
          if (itemPresentation != null) {
            return itemPresentation.getPresentableText();
          }
        }

        return getNode().getElementType() == MarkdownElementTypes.ORDERED_LIST
               ? "Ordered list"
               : "Unordered list";
      }

      @Override
      public String getLocationString() {
        PsiElement parent = getParent();
        if (parent instanceof MarkdownListItemImpl) {
          ItemPresentation itemPresentation = parent.getNode().getPsi(MarkdownListItemImpl.class).getPresentation();
          if (itemPresentation != null) {
            return itemPresentation.getLocationString();
          }
        }

        return null;
      }

      @Override
      public Icon getIcon(final boolean open) {
        PsiElement parent = getParent();
        if (parent instanceof MarkdownListItemImpl) {
          return null;
        }
        return AllIcons.Actions.ListFiles;
      }
    };
  }
}
