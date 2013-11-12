package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.lookup.*;
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
      @NotNull PostfixTemplateContext context, @NotNull List<LookupElement> consumer) {

    PrefixExpressionContext expression = context.outerExpression;
    if (!expression.canBeStatement) return;

    Boolean isNullable = NotNullCheckTemplateProvider.isNullableExpression(expression);
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

    @Override protected String getTemplate() { return "if(expr==null)"; }
  }
}