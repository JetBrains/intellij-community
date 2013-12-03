package org.jetbrains.postfixCompletion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.PsiElementResult;
import com.intellij.codeInsight.template.Result;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiPointerExpression extends Expression {
  @NotNull private final SmartPsiElementPointer<PsiExpression> valuePointer;

  public PsiPointerExpression(@NotNull SmartPsiElementPointer<PsiExpression> valuePointer) {
    this.valuePointer = valuePointer;
  }

  @Nullable
  @Override
  public Result calculateResult(ExpressionContext expressionContext) {
    return new PsiElementResult(valuePointer.getElement());
  }

  @Nullable
  @Override
  public Result calculateQuickResult(ExpressionContext expressionContext) {
    return calculateResult(expressionContext);
  }

  @Nullable
  @Override
  public LookupElement[] calculateLookupItems(ExpressionContext expressionContext) {
    return LookupElement.EMPTY_ARRAY;
  }
}
