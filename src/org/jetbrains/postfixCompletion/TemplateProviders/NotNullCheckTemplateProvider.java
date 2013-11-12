package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.lookup.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;
import org.jetbrains.postfixCompletion.LookupItems.*;

import java.util.*;

@TemplateProvider(
  templateName = "notnull",
  description = "Checks expression to be not-null",
  example = "if (expr != null)")
public class NotNullCheckTemplateProvider extends TemplateProviderBase {
  @Override public void createItems(
    @NotNull PostfixTemplateContext context, @NotNull List<LookupElement> consumer) {

    PrefixExpressionContext expression = context.outerExpression;
    if (!expression.canBeStatement) return;

    Boolean isNullable = isNullableExpression(expression);
    if (isNullable != null) {
      if (!isNullable) return;
    } else { // unknown nullability
      if (!context.executionContext.isForceMode) return;
    }

    consumer.add(new CheckNotNullLookupElement(expression));
  }

  @Nullable public static Boolean isNullableExpression(@NotNull PrefixExpressionContext context) {
    return isNullableExpression(context.expression, context.expressionType);
  }

  @Nullable private static Boolean isNullableExpression(@Nullable PsiExpression expression) {
    if (expression == null) return null;
    return isNullableExpression(expression, expression.getType());
  }

  @Nullable private static Boolean isNullableExpression(
    @NotNull PsiExpression expression, @Nullable PsiType expressionType) {
    if (expressionType != null) {
      return !(expressionType instanceof PsiPrimitiveType);
    }

    if (expression instanceof PsiPostfixExpression) return false;
    if (expression instanceof PsiPrefixExpression) return false;
    if (expression instanceof PsiBinaryExpression) return false;
    if (expression instanceof PsiPolyadicExpression) return false;
    if (expression instanceof PsiThisExpression) return false;
    if (expression instanceof PsiSuperExpression) return false;
    if (expression instanceof PsiClassObjectAccessExpression) return false;

    if (expression instanceof PsiParenthesizedExpression) {
      return isNullableExpression(((PsiParenthesizedExpression) expression).getExpression());
    }

    // todo: support ?: expression?
    return null;
  }

  private static final class CheckNotNullLookupElement extends NullCheckLookupElementBase {
    public CheckNotNullLookupElement(@NotNull PrefixExpressionContext context) {
      super("notnull", context);
    }

    @Override public Set<String> getAllLookupStrings() {
      Set<String> strings = super.getAllLookupStrings();
      strings.add("notNull");
      strings.add("notNull ");

      return strings;
    }

    @Override protected String getTemplate() {
      return "if(expr!=null)";
    }
  }
}