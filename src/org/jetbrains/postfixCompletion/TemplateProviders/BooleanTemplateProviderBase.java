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
    @NotNull PostfixTemplateAcceptanceContext context, @NotNull List<LookupElement> consumer) {

    for (PrefixExpressionContext expression : context.expressions)
      if (isBooleanExpression(expression) &&
          createBooleanItems(expression, consumer)) return;

    if (context.isForceMode)
      for (PrefixExpressionContext expression : context.expressions)
        if (createBooleanItems(expression, consumer)) return;
  }

  public static boolean isBooleanExpression(@NotNull PrefixExpressionContext context) {
    PsiType expressionType = context.expressionType;
    if (expressionType != null) {
      return PsiType.BOOLEAN.isAssignableFrom(expressionType);
    }

    PsiExpression expression = context.expression;
    if (expression instanceof PsiBinaryExpression) {
      PsiBinaryExpression binary = (PsiBinaryExpression) expression;
      IElementType tokenType = binary.getOperationSign().getTokenType();

      if (tokenType == JavaTokenType.GE || // x >= y
          tokenType == JavaTokenType.LE || // x <= y
          tokenType == JavaTokenType.LT || // x < y
          tokenType == JavaTokenType.GT || // x > y
          tokenType == JavaTokenType.NE || // x != y
          tokenType == JavaTokenType.EQEQ || // x == y
          tokenType == JavaTokenType.ANDAND || // x && y
          tokenType == JavaTokenType.OROR || // x || y
          tokenType == JavaTokenType.INSTANCEOF_KEYWORD) {
        return true; // TODO: other?

        //tokenType == JavaTokenType.AND || // x & y
        //tokenType == JavaTokenType.OR || // x | y
        //tokenType == JavaTokenType.XOR || // x ^ y
        // todo: check operand type?
      }
    }

    return false;
  }
}
