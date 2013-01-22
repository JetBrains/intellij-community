package com.jetbrains.rest.actions;

import com.intellij.codeInsight.editorActions.fillParagraph.ParagraphFillHandler;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.rest.RestFile;
import com.jetbrains.rest.RestTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : ktisha
 */
public class RestFillParagraphHandler extends ParagraphFillHandler {

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
