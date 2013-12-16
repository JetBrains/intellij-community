package org.jetbrains.postfixCompletion.templates;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.postfixCompletion.util.CommonUtils;

abstract public class BooleanPostfixTemplate extends PostfixTemplate {
  protected BooleanPostfixTemplate(@Nullable String name, @NotNull String description, @NotNull String example) {
    super(name, description, example);
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    PsiExpression topmostExpression = getTopmostExpression(context);
    return topmostExpression != null &&
           topmostExpression.getParent() instanceof PsiExpressionStatement &&
           CommonUtils.isBoolean(topmostExpression.getType());
  }
}
