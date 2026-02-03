// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement;
import org.intellij.plugins.markdown.structureView.MarkdownBasePresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public abstract class MarkdownCompositePsiElementBase extends ASTWrapperPsiElement implements MarkdownCompositePsiElement {
  public static final int PRESENTABLE_TEXT_LENGTH = 50;

  public MarkdownCompositePsiElementBase(@NotNull ASTNode node) {
    super(node);
  }

  protected @NotNull CharSequence getChars() {
    return getTextRange().subSequence(getContainingFile().getViewProvider().getContents());
  }

  protected @NotNull String shrinkTextTo(int length) {
    final CharSequence chars = getChars();
    return chars.subSequence(0, Math.min(length, chars.length())).toString();
  }

  @Override
  public @NotNull List<MarkdownPsiElement> getCompositeChildren() {
    return Arrays.asList(findChildrenByClass(MarkdownPsiElement.class));
  }

  /**
   * @return {@code true} if there is more than one composite child
   * OR there is one child which is not a paragraph, {@code false} otherwise.
   */
  public boolean hasTrivialChildren() {
    final Collection<MarkdownPsiElement> children = getCompositeChildren();
    if (children.size() != 1) {
      return false;
    }
    return children.iterator().next() instanceof MarkdownParagraph;
  }

  @Override
  public ItemPresentation getPresentation() {
    return new MarkdownBasePresentation() {
      @Override
      public @Nullable String getPresentableText() {
        if (!isValid()) {
          return null;
        }
        return getPresentableTagName();
      }

      @Override
      public @Nullable String getLocationString() {
        if (!isValid()) {
          return null;
        }
        if (getCompositeChildren().isEmpty()) {
          return shrinkTextTo(PRESENTABLE_TEXT_LENGTH);
        }
        else {
          return null;
        }
      }
    };
  }
}
