package org.jetbrains.postfixCompletion.templates;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class SynchronizedStatementPostfixTemplate extends StatementPostfixTemplate {
  public SynchronizedStatementPostfixTemplate() {
    super("synchronized", "Produces synchronization statement", "synchronized (expr)");
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    PsiExpression expression = getTopmostExpression(context);
    PsiElement parent = expression != null ? expression.getParent() : null;
    return parent instanceof PsiExpressionStatement && goodEnoughType(expression);
  }

  private static boolean goodEnoughType(@NotNull PsiExpression expression) {
    PsiType expressionType = expression.getType();
    return !(expressionType instanceof PsiPrimitiveType) && expressionType != null;
    // if (!expressionType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) return null;  // todo: what does it mean?
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    surroundWith(context, editor, "synchronized");
  }
}