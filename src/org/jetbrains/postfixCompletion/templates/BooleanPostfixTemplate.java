package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.postfixCompletion.infrastructure.PostfixTemplateContext;
import org.jetbrains.postfixCompletion.infrastructure.PrefixExpressionContext;

public abstract class BooleanPostfixTemplate extends PostfixTemplate {
  public abstract LookupElement createLookupElement(@NotNull PrefixExpressionContext context);

  @Override
  public LookupElement createLookupElement(@NotNull PostfixTemplateContext context) {
    for (PrefixExpressionContext expression : context.expressions()) {
      if (isBooleanExpression(expression)) {
        LookupElement element = createLookupElement(expression);
        if (element != null) return element;
      }
    }

    if (context.executionContext.isForceMode) {
      for (PrefixExpressionContext expression : context.expressions()) {
        LookupElement element = createLookupElement(expression);
        if (element != null) return element;
      }
    }

    return null;
  }

  public static boolean isBooleanExpression(@NotNull PrefixExpressionContext context) {
    return context.expression instanceof PsiExpression &&
           isBooleanExpression((PsiExpression)context.expression, context.expressionType);
  }

  private static boolean isBooleanExpression(@Nullable PsiExpression expression) {
    return expression != null && isBooleanExpression(expression, expression.getType());
  }

  private static boolean isBooleanExpression(@NotNull PsiExpression expression, @Nullable PsiType expressionType) {
    if (expressionType != null) {
      return PsiType.BOOLEAN.isAssignableFrom(expressionType);
    }

    IElementType sign;
    if (expression instanceof PsiBinaryExpression) {
      PsiBinaryExpression binary = (PsiBinaryExpression)expression;
      sign = binary.getOperationSign().getTokenType();
    }
    else if (expression instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression polyadic = (PsiPolyadicExpression)expression;
      sign = polyadic.getOperationTokenType();
    }
    else if (expression instanceof PsiPrefixExpression) {
      PsiPrefixExpression prefix = (PsiPrefixExpression)expression;
      return prefix.getOperationSign() == JavaTokenType.EXCL; // !x
    }
    else if (expression instanceof PsiParenthesizedExpression) {
      PsiParenthesizedExpression parenthesized = (PsiParenthesizedExpression)expression;
      return isBooleanExpression(parenthesized.getExpression());
    }
    else {
      return false;
    }

    // expressions always of boolean type
    if (sign == JavaTokenType.GE || // x >= y
        sign == JavaTokenType.LE || // x <= y
        sign == JavaTokenType.LT || // x < y
        sign == JavaTokenType.GT || // x > y
        sign == JavaTokenType.NE || // x != y
        sign == JavaTokenType.EQEQ || // x == y
        sign == JavaTokenType.ANDAND || // x && y
        sign == JavaTokenType.OROR || // x || y
        sign == JavaTokenType.INSTANCEOF_KEYWORD) {
      return true;
    }

    // expression possible of boolean type
    if (sign == JavaTokenType.AND || // x & y
        sign == JavaTokenType.OR || // x | y
        sign == JavaTokenType.XOR) {    // x ^ y

      if (expression instanceof PsiBinaryExpression) {
        PsiBinaryExpression binary = (PsiBinaryExpression)expression;
        return isBooleanExpression(binary.getLOperand()) ||
               isBooleanExpression(binary.getROperand());
      }
      else {
        PsiPolyadicExpression polyadic = (PsiPolyadicExpression)expression;
        for (PsiExpression operand : polyadic.getOperands()) {
          if (isBooleanExpression(operand)) return true;
        }
      }
    }

    return false;
  }
}