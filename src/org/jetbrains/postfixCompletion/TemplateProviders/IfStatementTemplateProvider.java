package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.Infrastructure.PostfixTemplateAcceptanceContext;
import org.jetbrains.postfixCompletion.Infrastructure.PrefixExpressionContext;
import org.jetbrains.postfixCompletion.Infrastructure.TemplateProvider;
import org.jetbrains.postfixCompletion.LookupItems.StatementPostfixLookupElement;

import java.util.List;

@TemplateProvider(
  templateName = "if",
  description = "Checks boolean expression to be 'true'",
  example = "if (expr)")
public class IfStatementTemplateProvider extends TemplateProviderBase {
  @Override public void createItems(
    @NotNull final PostfixTemplateAcceptanceContext context,
    @NotNull final List<LookupElement> consumer) {

    // todo: handle force mode
    // todo: handle unknown type?

    for (final PrefixExpressionContext expressionContext : context.expressions) {
      if (isBooleanExpression(expressionContext)) {
        consumer.add(new IfLookupItem(expressionContext));
        break;
      }
    }
  }

  private static boolean isBooleanExpression(
    @NotNull final PrefixExpressionContext context) {

    final PsiType expressionType = context.expressionType;
    if (expressionType != null) {
      if (PsiType.BOOLEAN.isAssignableFrom(expressionType)) {
        return true;
      }

    } else {
      final PsiExpression expression = context.expression;
      if (expression instanceof PsiBinaryExpression) {
        final PsiJavaToken operationSign = ((PsiBinaryExpression) expression).getOperationSign();
        final IElementType tokenType = operationSign.getTokenType();
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
            tokenType == JavaTokenType.INSTANCEOF_KEYWORD // todo: make it work
          ) {
          return true; // TODO: other
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

