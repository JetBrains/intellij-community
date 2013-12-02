package org.jetbrains.postfixCompletion.templates;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public final class AssertStatementPostfixTemplate extends BooleanPostfixTemplate2 {

  public AssertStatementPostfixTemplate() {
    super("assert", ".assert");
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    PsiExpression expression = getTopmostExpression(context);
    assert expression != null;
    PsiElement statement = expression.getParent();
    assert statement instanceof PsiStatement;
    PsiElementFactory factory = JavaPsiFacade.getInstance(context.getProject()).getElementFactory();
    PsiAssertStatement assertStatement = (PsiAssertStatement)factory.createStatementFromText("assert expr;", expression);
    PsiExpression condition = assertStatement.getAssertCondition();
    assert condition != null;
    condition.replace(expression);
    PsiElement newStatement = statement.replace(assertStatement);
    editor.getCaretModel().moveToOffset(newStatement.getTextRange().getEndOffset());
  }
}