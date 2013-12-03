package org.jetbrains.postfixCompletion.templates;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.util.JavaSurroundersProxy;

public class CastExpressionPostfixTemplate extends ExpressionPostfixTemplateWithExpressionChooser {
  public CastExpressionPostfixTemplate() {
    super("cast", "Surrounds expression with cast", "((SomeType) expr)");
  }

  @Override
  protected void doIt(@NotNull final Editor editor, @NotNull final PsiExpression expression) {
    JavaSurroundersProxy.cast(expression.getProject(), editor, expression);
  }
}