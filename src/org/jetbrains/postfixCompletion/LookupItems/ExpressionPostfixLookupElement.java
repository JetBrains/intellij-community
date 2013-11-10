package org.jetbrains.postfixCompletion.LookupItems;

import com.intellij.codeInsight.completion.*;
import com.intellij.openapi.project.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;

public abstract class ExpressionPostfixLookupElement<TExpression extends PsiExpression>
  extends PostfixLookupElement<TExpression> {

  public ExpressionPostfixLookupElement(
    @NotNull String lookupString, @NotNull PrefixExpressionContext context) {
    super(lookupString, context);
  }

  @Override @NotNull protected TExpression handlePostfixInsert(
    @NotNull InsertionContext context, @NotNull PrefixExpressionContext expressionContext) {
    // get facade and factory while all elements are physical and valid
    Project project = expressionContext.expression.getProject();
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    PsiElementFactory elementFactory = psiFacade.getElementFactory();

    // fix up expression before template expansion
    PrefixExpressionContext fixedContext = expressionContext.fixUp();
    PsiExpression exprCopy = (PsiExpression) fixedContext.expression.copy();

    TExpression newExpression = createNewExpression(elementFactory, exprCopy, fixedContext.expression);

    //noinspection unchecked
    return (TExpression) fixedContext.expression.replace(newExpression);
  }

  @NotNull protected abstract TExpression createNewExpression(
    @NotNull PsiElementFactory factory, @NotNull PsiExpression expression, @NotNull PsiElement context);

  @Override protected void postProcess(
      @NotNull InsertionContext context, @NotNull TExpression expression) {
    int offset = expression.getTextRange().getEndOffset();
    context.getEditor().getCaretModel().moveToOffset(offset);
  }
}