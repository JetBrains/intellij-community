package org.jetbrains.postfixCompletion.lookupItems;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiJavaToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.infrastructure.PrefixExpressionContext;

public abstract class IfStatementPostfixLookupItem extends StatementPostfixLookupElement<PsiIfStatement> {
  public IfStatementPostfixLookupItem(@NotNull String lookupString, @NotNull PrefixExpressionContext context) {
    super(lookupString, context);
  }

  protected abstract void processStatement(
    @NotNull PsiElementFactory factory, @NotNull PsiIfStatement ifStatement, @NotNull PsiElement expression);

  @NotNull @Override protected PsiIfStatement createNewStatement(
    @NotNull PsiElementFactory factory, @NotNull PsiElement expression, @NotNull PsiElement context) {

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
