package org.jetbrains.postfixCompletion.Infrastructure;

import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;

public final class PrefixExpressionContext {
  @NotNull private final PostfixTemplateAcceptanceContext myParentContext;
  @NotNull private final PsiExpression myExpression;

  public PrefixExpressionContext(@NotNull final PostfixTemplateAcceptanceContext parentContext,
                                 @NotNull final PsiExpression expression) {

    myParentContext = parentContext;
    myExpression = expression;
  }

  @NotNull public final PostfixTemplateAcceptanceContext getParentContext() {
    return myParentContext;
  }

  @NotNull public final PsiExpression getExpression() {
    return myExpression;
  }

  public final boolean canBeStatement() {
    return false; //TODO
  }
}
