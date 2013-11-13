package org.jetbrains.postfixCompletion.LookupItems;

import com.intellij.codeInsight.completion.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;

public abstract class IfStatementPostfixLookupItem extends StatementPostfixLookupElement<PsiIfStatement> {
  public IfStatementPostfixLookupItem(@NotNull String lookupString, @NotNull PrefixExpressionContext context) {
    super(lookupString, context);
  }

  protected abstract void processStatement(
    @NotNull PsiElementFactory factory, @NotNull PsiIfStatement ifStatement, @NotNull PsiExpression expression);

  @NotNull @Override protected PsiIfStatement createNewStatement(
    @NotNull PsiElementFactory factory, @NotNull PsiExpression expression, @NotNull PsiElement context) {

    PsiIfStatement ifStatement = (PsiIfStatement) factory.createStatementFromText("if(expr)", context);
    processStatement(factory, ifStatement, expression);

    return ifStatement;
  }

  @Override protected void postProcess(@NotNull InsertionContext context, @NotNull PsiIfStatement statement) {
    PsiJavaToken rParenth = statement.getRParenth();
    assert rParenth != null : "rParenth != null";

    int offset = rParenth.getTextRange().getEndOffset();
    context.getEditor().getCaretModel().moveToOffset(offset);
  }
}
