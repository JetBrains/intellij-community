package org.intellij.plugins.markdown.lang.psi.impl;

import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.intellij.plugins.markdown.lang.psi.MarkdownElementVisitor;
import org.intellij.plugins.markdown.util.MarkdownPsiUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class MarkdownList extends MarkdownCompositePsiElementBase {
  private static final String ORDERED_LIST_TEXT = "Ordered list";
  private static final String UNORDERED_LIST_TEXT = "Unordered list";

  public MarkdownList(@NotNull ASTNode node) {
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
        PsiElement[] parentChildren = getParent().getChildren();
        if (MarkdownPsiUtil.isSimpleNestedList(parentChildren)) {
          ItemPresentation parentPresentation = getParent().getNode().getPsi(MarkdownListItem.class).getPresentation();
          if (parentPresentation != null) {
            return parentPresentation.getPresentableText();
          }
        }

        return getNode().getElementType() == MarkdownElementTypes.ORDERED_LIST
               ? ORDERED_LIST_TEXT
               : UNORDERED_LIST_TEXT;
      }

      @Override
      public String getLocationString() {
        PsiElement[] parentChildren = getParent().getChildren();
        if (MarkdownPsiUtil.isSimpleNestedList(parentChildren)) {
          ItemPresentation parentPresentation = getParent().getNode().getPsi(MarkdownListItem.class).getPresentation();
          if (parentPresentation != null) {
            return parentPresentation.getLocationString();
          }
        }

        return null;
      }

      @Override
      public Icon getIcon(final boolean open) {
        return MarkdownPsiUtil.isSimpleNestedList(getParent().getChildren())
               ? null
               : AllIcons.Actions.ListFiles;
      }
    };
  }
}
