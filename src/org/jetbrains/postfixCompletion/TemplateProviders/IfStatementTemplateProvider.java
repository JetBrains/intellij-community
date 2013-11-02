package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.Infrastructure.PostfixTemplateAcceptanceContext;
import org.jetbrains.postfixCompletion.Infrastructure.PostfixTemplatesManager;
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

    for (final PrefixExpressionContext expressionContext : context.expressions) {

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

  private static final class IfLookupElement extends PostfixLookupItem {
    @NotNull private final Class<? extends PsiExpression> myExpressionType;
    @NotNull private final TextRange myExpressionRange;

    //private final PsiExpression myFoo;

    public IfLookupElement(@NotNull final PrefixExpressionContext context) {
      super("if");
      myExpressionType = context.expression.getClass();
      myExpressionRange = context.expression.getTextRange();
    }

    @Override
    public void handleInsert(InsertionContext context) {



      final PostfixTemplatesManager templatesManager =
        ApplicationManager.getApplication().getComponent(PostfixTemplatesManager.class);

      final int
        startOffset = context.getStartOffset(),
        endOffset = context.getTailOffset();

      // note: use 'postfix' string, to break expression like '0.postfix'
      context.getDocument().replaceString(startOffset, endOffset, "postfix");
      context.commitDocument();

      final PsiElement psiElement = context.getFile().findElementAt(startOffset);
      if (psiElement == null) {
        return;
      }

      final PostfixTemplateAcceptanceContext acceptanceContext = templatesManager.isAvailable(psiElement, true);
      if (acceptanceContext == null) {
        return;
      }

      final List<PrefixExpressionContext> expressions = acceptanceContext.expressions;
      for (PrefixExpressionContext expression : expressions) {
        final PsiExpression expr = expression.expression;
        if (myExpressionType.isInstance(expr) && expr.getTextRange().equals(myExpressionRange)) {

          final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(expr.getProject());
          final PsiElementFactory psiElementFactory = psiFacade.getElementFactory();
          final PsiIfStatement psiStatement = (PsiIfStatement)
            psiElementFactory.createStatementFromText("if(expr){CARET;}", expr);

          PsiExpression condition = psiStatement.getCondition();
          assert condition != null;

          condition.replace(expr);

          PsiIfStatement newSt = (PsiIfStatement) parent1.replace(psiStatement);

          // do magic
          break;
        }
      }
    }
  }
}

