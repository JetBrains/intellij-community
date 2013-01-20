package com.jetbrains.python.actions;

import com.intellij.codeInsight.editorActions.fillParagraph.ParagraphFillHandler;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : ktisha
 */
public class PyFillParagraphHandler extends ParagraphFillHandler {

  @NotNull
  protected String getPrefix(@NotNull final PsiElement element) {
    return element instanceof PsiComment? "#" : "";
  }

  @Override
  protected boolean isAvailableForElement(@Nullable PsiElement element) {
    if (element != null) {
      final PyStringLiteralExpression stringLiteral = PsiTreeUtil
        .getParentOfType(element, PyStringLiteralExpression.class);
      return stringLiteral != null || element instanceof PsiComment;
    }
    return false;
  }

  @Override
  protected boolean isAvailableForFile(@Nullable PsiFile psiFile) {
    return psiFile instanceof PyFile;
  }
}
