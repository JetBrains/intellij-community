package org.jetbrains.postfixCompletion.LookupItems;

import com.intellij.codeInsight.completion.*;
import com.intellij.openapi.project.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;

public abstract class StatementPostfixLookupElement<TStatement extends PsiStatement>
  extends PostfixLookupElement<TStatement> {

  public StatementPostfixLookupElement(
    @NotNull String lookupString, @NotNull PrefixExpressionContext context) {
    super(lookupString, context);
  }

  @Override @NotNull protected TStatement handlePostfixInsert(
    @NotNull InsertionContext context, @NotNull PrefixExpressionContext expressionContext) {
    // get facade and factory while all elements are physical and valid
    Project project = expressionContext.expression.getProject();
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    PsiElementFactory psiElementFactory = psiFacade.getElementFactory();

    // fix up expression before template expansion
    PrefixExpressionContext fixedContext = expressionContext.fixUp();

    // get target statement to replace
    PsiStatement targetStatement = fixedContext.getContainingStatement();
    assert targetStatement != null : "targetStatement != null";

    PsiExpression exprCopy = (PsiExpression) fixedContext.expression.copy();
    TStatement newStatement = createNewStatement(
      psiElementFactory, exprCopy, fixedContext.expression);

    //noinspection unchecked
    return (TStatement) targetStatement.replace(newStatement);
  }

  @NotNull protected abstract TStatement createNewStatement(
    @NotNull PsiElementFactory factory, @NotNull PsiExpression expression, @NotNull PsiElement context);

  @Override protected void postProcess(
    @NotNull InsertionContext context, @NotNull TStatement statement) {
    int offset = statement.getTextRange().getEndOffset();
    context.getEditor().getCaretModel().moveToOffset(offset);
  }
}