package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.lookup.*;
import com.intellij.psi.*;
import com.intellij.psi.tree.*;
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
    return isBooleanExpression(context.expression, context.expressionType);
  }

  private static boolean isBooleanExpression(@Nullable PsiExpression expression) {
    if (expression == null) return false;
    return isBooleanExpression(expression, expression.getType());
  }

  private static boolean isBooleanExpression(@NotNull PsiExpression expression, @Nullable PsiType expressionType) {
    if (expressionType != null) {
      return PsiType.BOOLEAN.isAssignableFrom(expressionType);
    }

    IElementType sign;
    if (expression instanceof PsiBinaryExpression) {
      PsiBinaryExpression binary = (PsiBinaryExpression) expression;
      sign = binary.getOperationSign().getTokenType();
    }
    else if (expression instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression polyadic = (PsiPolyadicExpression) expression;
      sign = polyadic.getOperationTokenType();
    }
    else if (expression instanceof PsiPrefixExpression) {
      PsiPrefixExpression prefix = (PsiPrefixExpression) expression;
      return prefix.getOperationSign() == JavaTokenType.EXCL; // !x
    }
    else if (expression instanceof PsiParenthesizedExpression) {
      PsiParenthesizedExpression parenthesized = (PsiParenthesizedExpression) expression;
      return isBooleanExpression(parenthesized.getExpression());
    }
    else return false;

    // expressions always of boolean type
    if (sign == JavaTokenType.GE     || // x >= y
        sign == JavaTokenType.LE     || // x <= y
        sign == JavaTokenType.LT     || // x < y
        sign == JavaTokenType.GT     || // x > y
        sign == JavaTokenType.NE     || // x != y
        sign == JavaTokenType.EQEQ   || // x == y
        sign == JavaTokenType.ANDAND || // x && y
        sign == JavaTokenType.OROR   || // x || y
        sign == JavaTokenType.INSTANCEOF_KEYWORD) return true;

    // expression possible of boolean type
    if (sign == JavaTokenType.AND    || // x & y
        sign == JavaTokenType.OR     || // x | y
        sign == JavaTokenType.XOR) {    // x ^ y

      if (expression instanceof PsiBinaryExpression) {
        PsiBinaryExpression binary = (PsiBinaryExpression) expression;
        return isBooleanExpression(binary.getLOperand())
            || isBooleanExpression(binary.getROperand());
      } else {
        PsiPolyadicExpression polyadic = (PsiPolyadicExpression) expression;
        for (PsiExpression operand : polyadic.getOperands()) {
          if (isBooleanExpression(operand)) return true;
        }
      }
    }

    return false;
  }
}
