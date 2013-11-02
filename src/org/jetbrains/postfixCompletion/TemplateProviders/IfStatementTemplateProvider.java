package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.Infrastructure.PostfixTemplateAcceptanceContext;
import org.jetbrains.postfixCompletion.Infrastructure.PrefixExpressionContext;
import org.jetbrains.postfixCompletion.Infrastructure.TemplateProvider;
import org.jetbrains.postfixCompletion.LookupItems.PostfixLookupItem;

import java.util.List;

@TemplateProvider(
  templateName = "if",
  description = "Checks boolean expression to be 'true'",
  example = "if (expr)")
public class IfStatementTemplateProvider extends TemplateProviderBase {
  @Override public void createItems(
    @NotNull final PostfixTemplateAcceptanceContext context,
    @NotNull final List<LookupElement> consumer) {

    // todo: handle Boolean?
    // todo: handle force mode
    // todo: handle unknown type?

    for (final PrefixExpressionContext expressionContext : context.getExpressions()) {

      if (isBooleanExpression(expressionContext)) {
        final IfLookupElement lookupElement = new IfLookupElement(expressionContext);
        consumer.add(lookupElement);
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
        if (tokenType == JavaTokenType.GE ||
            tokenType == JavaTokenType.LE ||
            tokenType == JavaTokenType.LT ||
            tokenType == JavaTokenType.GT ||
            tokenType == JavaTokenType.NE ||
            tokenType == JavaTokenType.EQEQ ||
            tokenType == JavaTokenType.ANDAND ||
            tokenType == JavaTokenType.AND ||
            tokenType == JavaTokenType.OROR ||
            tokenType == JavaTokenType.OR ||
            tokenType == JavaTokenType.XOR ||
            tokenType == JavaTokenType.INSTANCEOF_KEYWORD) {
          return true; // TODO: other
        }
      }
    }

    return false;
  }

  private static final class IfLookupElement extends PostfixLookupItem {


    //private final PsiExpression myFoo;

    public IfLookupElement(@NotNull final PrefixExpressionContext context) {
      super("if");

      //myFoo = context.expression;

      //context.expression
    }

    @Override
    public void handleInsert(InsertionContext context) {

      //ApplicationManager.getApplication()


      super.handleInsert(context);


    }
  }
}

