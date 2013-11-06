package org.jetbrains.postfixCompletion.Infrastructure;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PrefixExpressionContext {
  @NotNull public final PostfixTemplateAcceptanceContext parentContext;
  @NotNull public final PsiExpression expression;
  @Nullable public final PsiType expressionType;
  public final boolean canBeStatement;

  public PrefixExpressionContext(@NotNull final PostfixTemplateAcceptanceContext parentContext,
                                 @NotNull final PsiExpression expression) {
    this.parentContext = parentContext;
    this.expression = expression;
    expressionType = expression.getType();
    canBeStatement = getContainingStatement() != null;
  }

  @Nullable public final PsiStatement getContainingStatement() {
    // look for expression-statement parent
    final PsiElement parent = expression.getParent();
    if (parent instanceof PsiExpressionStatement)
      return (PsiStatement) parent;

    return null;
  }

  @NotNull public final PrefixExpressionContext fixUp() {
    return parentContext.fixUpExpression(this);
  }
}
