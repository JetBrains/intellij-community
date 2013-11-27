package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiParenthesizedExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.CommonUtils;
import org.jetbrains.postfixCompletion.infrastructure.PostfixTemplateContext;
import org.jetbrains.postfixCompletion.infrastructure.PrefixExpressionContext;
import org.jetbrains.postfixCompletion.infrastructure.TemplateProvider;
import org.jetbrains.postfixCompletion.lookupItems.ExpressionPostfixLookupElement;

import java.util.List;

@TemplateProvider(
  templateName = "par",
  description = "Parenthesizes current expression",
  example = "(expr)",
  worksInsideFragments = true)
public final class ParenthesizedExpressionPostfixTemplateProvider extends PostfixTemplateProvider {
  @Override public void createItems(
      @NotNull PostfixTemplateContext context, @NotNull List<LookupElement> consumer) {
    if (!context.executionContext.isForceMode) return;

    List<PrefixExpressionContext> expressions = context.expressions();
    PrefixExpressionContext bestContext = context.outerExpression();

    for (int index = expressions.size() - 1; index >= 0; index--) {
      PrefixExpressionContext expressionContext = expressions.get(index);

      if (CommonUtils.isNiceExpression(expressionContext.expression)) {
        bestContext = expressionContext;
        break;
      }
    }

    consumer.add(new ParenthesizeLookupElement(bestContext));
  }

  private static class ParenthesizeLookupElement extends ExpressionPostfixLookupElement<PsiParenthesizedExpression> {
    public ParenthesizeLookupElement(@NotNull PrefixExpressionContext context) {
      super("par", context);
    }

    @NotNull @Override protected PsiParenthesizedExpression createNewExpression(
      @NotNull PsiElementFactory factory, @NotNull PsiElement expression, @NotNull PsiElement context) {

      PsiParenthesizedExpression parenthesizedExpression =
        (PsiParenthesizedExpression) factory.createExpressionFromText("(expr)", context);

      PsiExpression operand = parenthesizedExpression.getExpression();
      assert (operand != null) : "operand != null";

      operand.replace(expression);

      return parenthesizedExpression;
    }
  }
}