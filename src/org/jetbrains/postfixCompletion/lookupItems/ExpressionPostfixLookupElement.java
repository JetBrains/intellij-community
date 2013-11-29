package org.jetbrains.postfixCompletion.lookupItems;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.infrastructure.PrefixExpressionContext;

/**
 * @author ignatov
 */
public class ExpressionPostfixLookupElement extends ExpressionPostfixLookupElementBase<PsiExpression> {
  public ExpressionPostfixLookupElement(@NotNull String lookupString, @NotNull PrefixExpressionContext context) {
    super(lookupString, context);
  }

  @NotNull
  @Override
  protected PsiExpression createNewExpression(@NotNull PsiElementFactory factory, @NotNull PsiElement expression, @NotNull PsiElement context) {
    // change type of PrefixExpressionContext.myExpression?
    return ((PsiExpression)expression);
  }
}
