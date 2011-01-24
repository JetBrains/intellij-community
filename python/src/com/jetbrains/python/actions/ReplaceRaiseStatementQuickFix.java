package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyRaiseStatement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexey.Ivanov
 */
public class ReplaceRaiseStatementQuickFix implements LocalQuickFix {
  @NotNull
  @Override
  public String getName() {
    return PyBundle.message("INTN.replace.raise.statement");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.Family.migration.to.python3");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement raiseStatement = descriptor.getPsiElement();
    if (raiseStatement instanceof PyRaiseStatement) {
      PyExpression[] expressions = ((PyRaiseStatement)raiseStatement).getExpressions();
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      String newExpressionText = expressions[0].getText() + "(" + expressions[1].getText() + ")";
      if (expressions.length == 2) {
        raiseStatement.replace(elementGenerator.createFromText(LanguageLevel.forElement(raiseStatement), PyRaiseStatement.class, "raise " + newExpressionText));
      } else if (expressions.length == 3) {
        raiseStatement.replace(elementGenerator.createFromText(LanguageLevel.forElement(raiseStatement), PyRaiseStatement.class,
                                                               "raise " + newExpressionText + ".with_traceback(" + expressions[2].getText() + ")"));
      }
    }
  }
}