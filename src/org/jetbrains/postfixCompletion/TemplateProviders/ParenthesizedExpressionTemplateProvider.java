package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.lookup.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;
import org.jetbrains.postfixCompletion.LookupItems.*;

import java.util.*;

@TemplateProvider(
  templateName = "par",
  description = "Parenthesizes current expression",
  example = "(expr)",
  worksInsideFragments = true)
public class ParenthesizedExpressionTemplateProvider extends TemplateProviderBase {
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