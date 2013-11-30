package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.infrastructure.PostfixTemplateContext;
import org.jetbrains.postfixCompletion.infrastructure.PrefixExpressionContext;
import org.jetbrains.postfixCompletion.infrastructure.TemplateInfo;
import org.jetbrains.postfixCompletion.lookupItems.ExpressionPostfixLookupElement;
import org.jetbrains.postfixCompletion.util.CommonUtils;
import org.jetbrains.postfixCompletion.util.JavaSurroundersProxy;

import java.util.List;

@TemplateInfo(
  templateName = "cast",
  description = "Surrounds expression with cast",
  example = "((SomeType) expr)",
  worksInsideFragments = true)
public final class CastExpressionPostfixTemplate extends PostfixTemplate {
  @Override
  public LookupElement createLookupElement(@NotNull PostfixTemplateContext context) {
    if (!context.executionContext.isForceMode) return null;

    PrefixExpressionContext bestContext = context.outerExpression();
    List<PrefixExpressionContext> expressions = context.expressions();

    for (int index = expressions.size() - 1; index >= 0; index--) {
      PrefixExpressionContext expressionContext = expressions.get(index);
      if (CommonUtils.isNiceExpression(expressionContext.expression)) {
        bestContext = expressionContext;
        break;
      }
    }

    return new CastLookupElement(bestContext);
  }

  static final class CastLookupElement extends ExpressionPostfixLookupElement {
    public CastLookupElement(@NotNull PrefixExpressionContext context) {
      super("cast", context);
    }

    @Override
    protected void postProcess(@NotNull final InsertionContext context, @NotNull PsiExpression expression) {
      JavaSurroundersProxy.cast(context.getProject(), context.getEditor(), expression);
    }
  }
}