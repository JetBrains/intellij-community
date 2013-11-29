package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiParenthesizedExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.infrastructure.PostfixTemplateContext;
import org.jetbrains.postfixCompletion.infrastructure.PrefixExpressionContext;
import org.jetbrains.postfixCompletion.infrastructure.TemplateInfo;
import org.jetbrains.postfixCompletion.lookupItems.ExpressionPostfixLookupElementBase;
import org.jetbrains.postfixCompletion.util.CommonUtils;

import java.util.List;

@TemplateInfo(
  templateName = "par",
  description = "Parenthesizes current expression",
  example = "(expr)",
  worksInsideFragments = true)
public final class ParenthesizedExpressionPostfixTemplate extends PostfixTemplate {
  @Override
  public LookupElement createLookupElement(@NotNull PostfixTemplateContext context) {
    if (!context.executionContext.isForceMode) return null;

    List<PrefixExpressionContext> expressions = context.expressions();
    PrefixExpressionContext bestContext = context.outerExpression();

    for (int index = expressions.size() - 1; index >= 0; index--) {
      PrefixExpressionContext expressionContext = expressions.get(index);

      if (CommonUtils.isNiceExpression(expressionContext.expression)) {
        bestContext = expressionContext;
        break;
      }
    }

    return new ParenthesizeLookupElement(bestContext);
  }

  private static class ParenthesizeLookupElement extends ExpressionPostfixLookupElementBase<PsiParenthesizedExpression> {
    public ParenthesizeLookupElement(@NotNull PrefixExpressionContext context) {
      super("par", context);
    }

    @NotNull
    @Override
    protected PsiParenthesizedExpression createNewExpression(@NotNull PsiElementFactory factory,
                                                             @NotNull PsiElement expression,
                                                             @NotNull PsiElement context) {
      PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)factory.createExpressionFromText("(expr)", context);
      PsiExpression operand = parenthesizedExpression.getExpression();
      assert operand != null;
      operand.replace(expression);
      return parenthesizedExpression;
    }
  }
}