// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.actions;

import com.intellij.codeInsight.editorActions.fillParagraph.ParagraphFillHandler;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.python.reStructuredText.RestFile;
import com.intellij.python.reStructuredText.RestTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : ktisha
 */
public class RestFillParagraphHandler extends ParagraphFillHandler {

  @Override
  @NotNull
  protected String getPrefix(@NotNull final PsiElement element) {
    return element instanceof PsiComment? ".. " : "";
  }

  @NotNull
  @Override
  protected String getPostfix(@NotNull PsiElement element) {
    return element.getNode().getElementType() == RestTokenTypes.COMMENT? "\n" : "";
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
  protected boolean atWhitespaceToken(@Nullable final PsiElement element) {
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
