package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.CodeInsightServicesUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

public class NotExpressionPostfixTemplate extends ExpressionPostfixTemplateWithExpressionChooser {
  public NotExpressionPostfixTemplate() {
    super("not", "Negates boolean expression", "!expr");
  }

  @Override
  protected void doIt(@NotNull Editor editor, @NotNull PsiExpression expression) {
    expression.replace(CodeInsightServicesUtil.invertCondition(expression));
  }

  @NotNull
  @Override
  protected Condition<PsiExpression> getTypeCondition() {
    return new Condition<PsiExpression>() {
      @Override
      public boolean value(PsiExpression expression) {
        return PsiType.BOOLEAN.equals(expression.getType()) || 
               PsiType.BOOLEAN.equals(PsiPrimitiveType.getUnboxedType(expression.getType()));
      }
    };
  }
}