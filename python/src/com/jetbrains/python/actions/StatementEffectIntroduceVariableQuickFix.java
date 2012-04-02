package com.jetbrains.python.actions;

import com.intellij.codeInsight.CodeInsightUtilBase;
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
  PsiElement myExpression;
  public StatementEffectIntroduceVariableQuickFix(PyExpression expression) {
    myExpression = expression;
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.introduce.variable");
  }

  @NonNls
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    if (myExpression != null && myExpression.isValid()) {
      final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      final PyAssignmentStatement assignment = elementGenerator.createFromText(LanguageLevel.forElement(myExpression), PyAssignmentStatement.class,
                                                         "var = " + myExpression.getText());
      
      myExpression = myExpression.replace(assignment);
      myExpression = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(myExpression);
      final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(myExpression);
      builder.replaceElement(((PyAssignmentStatement)myExpression).getLeftHandSideExpression(), "var");
      builder.run();
    }
  }
}
