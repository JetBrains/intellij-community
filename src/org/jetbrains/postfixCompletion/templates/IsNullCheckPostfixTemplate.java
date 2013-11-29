package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.infrastructure.PostfixTemplateContext;
import org.jetbrains.postfixCompletion.infrastructure.PrefixExpressionContext;
import org.jetbrains.postfixCompletion.infrastructure.TemplateInfo;
import org.jetbrains.postfixCompletion.lookupItems.NullCheckLookupElementBase;

@TemplateInfo(
  templateName = "null",
  description = "Checks expression to be null",
  example = "if (expr == null)")
public final class IsNullCheckPostfixTemplate extends PostfixTemplate {
  @Override
  public LookupElement createLookupElement(@NotNull PostfixTemplateContext context) {
    PrefixExpressionContext expression = context.outerExpression();
    if (!expression.canBeStatement) return null;

    Boolean isNullable = NotNullCheckPostfixTemplate.isNullableExpression(expression);
    if (isNullable != null) {
      if (!isNullable) return null;
    }
    else { // unknown nullability
      if (!context.executionContext.isForceMode) return null;
    }

    return new CheckIsNullLookupElement(expression);
  }

  private static final class CheckIsNullLookupElement extends NullCheckLookupElementBase {
    public CheckIsNullLookupElement(@NotNull PrefixExpressionContext context) {
      super("null", context);
    }

    @NotNull
    @Override
    protected String getConditionText() {
      return "expr==null";
    }
  }
}