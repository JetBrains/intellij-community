package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.infrastructure.PostfixTemplateContext;
import org.jetbrains.postfixCompletion.infrastructure.PrefixExpressionContext;
import org.jetbrains.postfixCompletion.infrastructure.TemplateProvider;
import org.jetbrains.postfixCompletion.lookupItems.NullCheckLookupElementBase;

import java.util.List;

@TemplateProvider(
  templateName = "null",
  description = "Checks expression to be null",
  example = "if (expr == null)")
public final class IsNullCheckPostfixTemplateProvider extends PostfixTemplateProvider {
  @Override public void createItems(
      @NotNull PostfixTemplateContext context, @NotNull List<LookupElement> consumer) {

    PrefixExpressionContext expression = context.outerExpression();
    if (!expression.canBeStatement) return;

    Boolean isNullable = NotNullCheckPostfixTemplateProvider.isNullableExpression(expression);
    if (isNullable != null) {
      if (!isNullable) return;
    } else { // unknown nullability
      if (!context.executionContext.isForceMode) return;
    }

    consumer.add(new CheckIsNullLookupElement(expression));
  }

  private static final class CheckIsNullLookupElement extends NullCheckLookupElementBase {
    public CheckIsNullLookupElement(@NotNull PrefixExpressionContext context) {
      super("null", context);
    }

    @NotNull @Override protected String getTemplate() { return "if(expr==null)"; }
  }
}