package org.jetbrains.postfixCompletion;

import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.template.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;

public final class PsiPointerExpression extends Expression {
  @NotNull private final SmartPsiElementPointer<PsiExpression> valuePointer;

  public PsiPointerExpression(@NotNull SmartPsiElementPointer<PsiExpression> valuePointer) {
    this.valuePointer = valuePointer;
  }

  @Nullable @Override public Result calculateResult(ExpressionContext expressionContext) {
    return new PsiElementResult(valuePointer.getElement());
  }

  @Nullable @Override public Result calculateQuickResult(ExpressionContext expressionContext) {
    return calculateResult(expressionContext);
  }

  @Nullable @Override public LookupElement[] calculateLookupItems(ExpressionContext expressionContext) {
    return LookupElement.EMPTY_ARRAY;
  }
}
