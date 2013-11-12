package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.lookup.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;

import java.util.*;

public abstract class BooleanTemplateProviderBase extends TemplateProviderBase {
  public abstract boolean createBooleanItems(
    @NotNull PrefixExpressionContext context, @NotNull List<LookupElement> consumer);

  @Override public void createItems(
    @NotNull PostfixTemplateContext context, @NotNull List<LookupElement> consumer) {

    for (PrefixExpressionContext expression : context.expressions)
      if (isBooleanExpression(expression) &&
          createBooleanItems(expression, consumer)) return;

    if (context.executionContext.isForceMode)
      for (PrefixExpressionContext expression : context.expressions)
        if (createBooleanItems(expression, consumer)) return;
  }

  public static boolean isBooleanExpression(@NotNull PrefixExpressionContext context) {
    PsiType expressionType = context.expressionType;
    if (expressionType != null) {
      return PsiType.BOOLEAN.isAssignableFrom(expressionType);
    }

    return false;
  }
}
