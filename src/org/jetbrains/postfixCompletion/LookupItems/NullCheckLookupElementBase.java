package org.jetbrains.postfixCompletion.lookupItems;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.infrastructure.PrefixExpressionContext;

public abstract class NullCheckLookupElementBase extends StatementPostfixLookupElement<PsiIfStatement> {
  public NullCheckLookupElementBase(@NotNull String lookupString, @NotNull PrefixExpressionContext context) {
    super(lookupString, context);
  }

  @NotNull @Override protected PsiIfStatement createNewStatement(
    @NotNull PsiElementFactory factory, @NotNull PsiElement expression, @NotNull PsiElement context) {
    PsiIfStatement ifStatement = (PsiIfStatement) factory.createStatementFromText(getTemplate(), context);

    PsiBinaryExpression condition = (PsiBinaryExpression) ifStatement.getCondition();
    assert condition != null : "condition != null";

    condition.getLOperand().replace(expression);

    return ifStatement;
  }

  @NotNull protected abstract String getTemplate();

  @Override protected void postProcess(@NotNull InsertionContext context, @NotNull PsiIfStatement statement) {
    PsiJavaToken rParenth = statement.getRParenth();
    assert rParenth != null : "rParenth != null";

    int offset = rParenth.getTextRange().getEndOffset();
    context.getEditor().getCaretModel().moveToOffset(offset);
  }
}
