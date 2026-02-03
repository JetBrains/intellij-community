// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.restructuredtext.actions;

import com.intellij.codeInsight.editorActions.fillParagraph.ParagraphFillHandler;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.restructuredtext.RestFile;
import com.intellij.restructuredtext.RestTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : ktisha
 */
public class RestFillParagraphHandler extends ParagraphFillHandler {

  @Override
  protected @NotNull String getPrefix(final @NotNull PsiElement element) {
    return element instanceof PsiComment? ".. " : "";
  }

  @Override
  protected @NotNull String getPostfix(@NotNull PsiElement element) {
    return element.getNode().getElementType() == RestTokenTypes.COMMENT ? "\n" : "";
  }

  @Override
  protected boolean isAvailableForFile(@Nullable PsiFile psiFile) {
    return psiFile instanceof RestFile;
  }

  @Override
  protected boolean isBunchOfElement(PsiElement element) {
    return true;
  }

  @Override
  protected boolean atWhitespaceToken(final @Nullable PsiElement element) {
    return element instanceof PsiWhiteSpace ||
           element != null && element.getNode().getElementType() == RestTokenTypes.WHITESPACE;
  }

  @Override
  protected void appendPostfix(@NotNull PsiElement element,
                               @NotNull String text,
                               @NotNull StringBuilder stringBuilder) {
    if (element.getNode().getElementType() == RestTokenTypes.COMMENT) {
      stringBuilder.append(getPostfix(element));
    }
  }
}
