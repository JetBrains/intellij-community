package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * Quickfix to introduce variable if statement seems to have no effect
 */
public class StatementEffectIntroduceVariableQuickFix implements LocalQuickFix {
  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.introduce.variable");
  }

  @NonNls
  @NotNull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    PsiElement expression = descriptor.getPsiElement();
    if (expression != null && expression.isValid()) {
      final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      final PyAssignmentStatement assignment = elementGenerator.createFromText(LanguageLevel.forElement(expression), PyAssignmentStatement.class,
                                                         "var = " + expression.getText());

      expression = expression.replace(assignment);
      if (expression == null) return;
      expression = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(expression);
      final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(expression);
      final PyExpression leftHandSideExpression = ((PyAssignmentStatement)expression).getLeftHandSideExpression();
      assert leftHandSideExpression != null;
      builder.replaceElement(leftHandSideExpression, "var");
      builder.run();
    }
  }
}
