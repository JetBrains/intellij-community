package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.postfixCompletion.infrastructure.PostfixTemplateContext;
import org.jetbrains.postfixCompletion.infrastructure.PrefixExpressionContext;
import org.jetbrains.postfixCompletion.infrastructure.TemplateInfo;
import org.jetbrains.postfixCompletion.lookupItems.NullCheckLookupElementBase;

import java.util.Set;

@TemplateInfo(
  templateName = "notnull",
  description = "Checks expression to be not-null",
  example = "if (expr != null)")
public final class NotNullCheckPostfixTemplate extends PostfixTemplate {
  @Override
  public LookupElement createLookupElement(@NotNull PostfixTemplateContext context) {
    PrefixExpressionContext expression = context.outerExpression();
    if (!expression.canBeStatement) return null;

    Boolean isNullable = isNullableExpression(expression);
    if (isNullable != null) {
      if (!isNullable) return null;
    }
    else { // unknown nullability
      if (!context.executionContext.isForceMode) return null;
    }

    return new CheckNotNullLookupElement(expression);
  }

  @Nullable
  public static Boolean isNullableExpression(@NotNull PrefixExpressionContext context) {
    if (context.expression instanceof PsiExpression) {
      return isNullableExpression((PsiExpression)context.expression, context.expressionType);
    }

    return Boolean.FALSE;
  }
  
  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    throw new UnsupportedOperationException("Implement me please");
  }

  @Nullable
  private static Boolean isNullableExpression(@Nullable PsiExpression expression) {
    if (expression == null) return null;
    return isNullableExpression(expression, expression.getType());
  }

  @Nullable
  private static Boolean isNullableExpression(@NotNull PsiExpression expression, @Nullable PsiType expressionType) {
    // filter out some known non-nullable expressions
    if (expression instanceof PsiPostfixExpression) return false;
    if (expression instanceof PsiPrefixExpression) return false;
    if (expression instanceof PsiBinaryExpression) return false;
    if (expression instanceof PsiPolyadicExpression) return false;
    if (expression instanceof PsiThisExpression) return false;
    if (expression instanceof PsiSuperExpression) return false;
    if (expression instanceof PsiClassObjectAccessExpression) return false;
    if (expression instanceof PsiNewExpression) return false;

    if (expression instanceof PsiParenthesizedExpression) {
      PsiParenthesizedExpression parenthesized = (PsiParenthesizedExpression)expression;
      return isNullableExpression(parenthesized.getExpression());
    }

    // todo: support ?: expression?

    if (expressionType != null) {
      return !(expressionType instanceof PsiPrimitiveType);
    }

    return null;
  }

  private static final class CheckNotNullLookupElement extends NullCheckLookupElementBase {
    public CheckNotNullLookupElement(@NotNull PrefixExpressionContext context) {
      super("notnull", context);
    }

    @Override
    public Set<String> getAllLookupStrings() {
      Set<String> strings = super.getAllLookupStrings();
      strings.add("notNull");
      strings.add("notNull ");

      return strings;
    }

    @NotNull
    @Override
    protected String getConditionText() {
      return "expr!=null";
    }
  }
}