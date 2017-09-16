package de.plushnikov.intellij.plugin.extension.postfix;

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateWithExpressionSelector;
import com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.refactoring.introduceVariable.IntroduceVariableHandler;
import org.jetbrains.annotations.NotNull;

public class LombokVarValPostfixTemplate extends PostfixTemplateWithExpressionSelector {

  private final String selectedTypeFQN;

  LombokVarValPostfixTemplate(String name, String example, String selectedTypeFQN) {
    super(name, example, JavaPostfixTemplatesUtils.selectorAllExpressionsWithCurrentOffset(JavaPostfixTemplatesUtils.IS_NON_VOID));
    this.selectedTypeFQN = selectedTypeFQN;
  }

  @Override
  protected void expandForChooseExpression(@NotNull PsiElement expression, @NotNull Editor editor) {
    IntroduceVariableHandler handler = new IntroduceLombokVariableHandler(selectedTypeFQN);
    handler.invoke(expression.getProject(), editor, (PsiExpression) expression);
  }

}
