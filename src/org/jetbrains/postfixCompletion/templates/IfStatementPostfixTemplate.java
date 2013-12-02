package org.jetbrains.postfixCompletion.templates;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.util.JavaSurroundersProxy;

public final class IfStatementPostfixTemplate extends BooleanPostfixTemplate2 {
  @Override
  public void expand(@NotNull PsiElement context, @NotNull final Editor editor) {
    PsiExpression expression = getTopmostExpression(context);
    assert expression != null;
    TextRange range = JavaSurroundersProxy.ifStatement(expression.getProject(), editor, expression);
    if (range != null) {
      editor.getCaretModel().moveToOffset(range.getStartOffset());
    }
  }

  @Override
  public String getName() {
    return "if";
  }
}

