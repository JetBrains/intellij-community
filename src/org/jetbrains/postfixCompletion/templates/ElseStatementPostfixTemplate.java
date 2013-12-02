package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.CodeInsightServicesUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.util.JavaSurroundersProxy;

public final class ElseStatementPostfixTemplate extends BooleanPostfixTemplate2 {
  public ElseStatementPostfixTemplate() {
    super("else");
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    PsiExpression expression = getTopmostExpression(context);
    assert expression != null;
    PsiExpression invertedExpression = (PsiExpression)expression.replace(CodeInsightServicesUtil.invertCondition(expression));
    TextRange range = JavaSurroundersProxy.ifStatement(invertedExpression.getProject(), editor, invertedExpression);
    if (range != null) {
      editor.getCaretModel().moveToOffset(range.getStartOffset());
    }
  }
}
