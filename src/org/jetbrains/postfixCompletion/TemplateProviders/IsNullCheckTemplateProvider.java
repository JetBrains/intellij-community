package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.lookup.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;
import org.jetbrains.postfixCompletion.LookupItems.*;

import java.util.*;

@TemplateProvider(
  templateName = "null",
  description = "Checks expression to be null",
  example = "if (expr == null)")
public class IsNullCheckTemplateProvider extends TemplateProviderBase {
  @Override public void createItems(
      @NotNull PostfixTemplateAcceptanceContext context, @NotNull List<LookupElement> consumer) {
    PrefixExpressionContext expression = context.outerExpression;

    if (expression.referencedElement instanceof PsiClass) return;
    if (expression.referencedElement instanceof PsiPackage) return;
    if (!expression.canBeStatement) return;

    PsiType expressionType = expression.expressionType;
    if (expressionType != null && !context.executionContext.isForceMode) {
      if (expressionType instanceof PsiPrimitiveType) return;
    }

    consumer.add(new CheckIsNullLookupElement(expression));
  }

  private static final class CheckIsNullLookupElement extends NullCheckLookupElementBase {
    public CheckIsNullLookupElement(@NotNull PrefixExpressionContext context) {
      super("null", context);
    }

    @Override protected String getTemplate() { return "if(expr==null)"; }
  }
}