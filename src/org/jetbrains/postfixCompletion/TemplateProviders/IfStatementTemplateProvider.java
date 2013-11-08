package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.lookup.*;
import com.intellij.psi.*;
import com.intellij.psi.tree.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;
import org.jetbrains.postfixCompletion.LookupItems.*;

import java.util.*;

@TemplateProvider(
  templateName = "if",
  description = "Checks boolean expression to be 'true'",
  example = "if (expr)")
public class IfStatementTemplateProvider extends TemplateProviderBase {
  @Override public void createItems(
    @NotNull final PostfixTemplateAcceptanceContext context,
    @NotNull final List<LookupElement> consumer) {




    /*
    * foreach (var expressionContext in context.Expressions)
      {
        if (expressionContext.Type.IsBool() ||
          IsBooleanExpression(expressionContext.Expression))
        {
          if (CreateBooleanItems(expressionContext, consumer)) return;
        }
      }

      if (context.ForceMode)
      {
        foreach (var expressionContext in context.Expressions)
        {
          if (CreateBooleanItems(expressionContext, consumer)) return;
        }
      }
    *
    * */



    // todo: handle force mode
    // todo: handle unknown type?
    // todo: use InvertIfConditionAction for .else/.not

    for (final PrefixExpressionContext expressionContext : context.expressions) {
      //expressionContext.expression instanceof PsiReferenceExpression.

      if (isBooleanExpression(expressionContext)) {
        consumer.add(new IfLookupItem(expressionContext));
        break;
      }
    }
  }

  public static boolean isBooleanExpression(
    @NotNull final PrefixExpressionContext context) {

    final PsiType expressionType = context.expressionType;
    if (expressionType != null) {
      if (PsiType.BOOLEAN.isAssignableFrom(expressionType)) {
        return true;
      }
    } else {
      final PsiExpression expression = context.expression;
      if (expression instanceof PsiBinaryExpression) {
        final PsiBinaryExpression binary = (PsiBinaryExpression) expression;
        final IElementType tokenType = binary.getOperationSign().getTokenType();

        if (tokenType == JavaTokenType.GE || // x >= y
            tokenType == JavaTokenType.LE || // x <= y
            tokenType == JavaTokenType.LT || // x < y
            tokenType == JavaTokenType.GT || // x > y
            tokenType == JavaTokenType.NE || // x != y
            tokenType == JavaTokenType.EQEQ || // x == y
            tokenType == JavaTokenType.ANDAND || // x && y
            //tokenType == JavaTokenType.AND || // x & y
            tokenType == JavaTokenType.OROR || // x || y
            //tokenType == JavaTokenType.OR || // x | y
            //tokenType == JavaTokenType.XOR || // x ^ y
            tokenType == JavaTokenType.INSTANCEOF_KEYWORD
          ) {
          return true; // TODO: other?
        }
      }
    }

    return false;
  }

  private static final class IfLookupItem
    extends StatementPostfixLookupElement<PsiIfStatement> {

    public IfLookupItem(@NotNull PrefixExpressionContext context) {
      super("if", context);
    }

    @NotNull @Override protected PsiIfStatement createNewStatement(
      @NotNull final PsiElementFactory factory,
      @NotNull final PsiExpression expression,
      @NotNull final PsiFile context) {

      final PsiIfStatement ifStatement = (PsiIfStatement)
        factory.createStatementFromText("if(expr)", context);

      final PsiExpression condition = ifStatement.getCondition();
      assert condition != null : "condition != null";
      condition.replace(expression);

      return ifStatement;
    }
  }
}

