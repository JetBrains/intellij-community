package org.jetbrains.postfixCompletion.LookupItems;

import com.intellij.psi.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;

public abstract class NullCheckLookupElementBase extends StatementPostfixLookupElement<PsiIfStatement> {
  public NullCheckLookupElementBase(@NotNull String lookupString, @NotNull PrefixExpressionContext context) {
    super(lookupString, context);
  }

  @NotNull @Override protected PsiIfStatement createNewStatement(
    @NotNull PsiElementFactory factory, @NotNull PsiExpression expression, @NotNull PsiElement context) {
    PsiIfStatement ifStatement = (PsiIfStatement) factory.createStatementFromText(getTemplate(), context);

    PsiBinaryExpression condition = (PsiBinaryExpression) ifStatement.getCondition();
    assert condition != null : "condition != null";

    condition.getLOperand().replace(expression);

    return ifStatement;
  }

  @NotNull protected abstract String getTemplate();
}
