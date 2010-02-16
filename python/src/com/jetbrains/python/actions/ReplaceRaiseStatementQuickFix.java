package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyRaiseStatement;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: 10.02.2010
 * Time: 19:24:17
 */
public class ReplaceRaiseStatementQuickFix implements LocalQuickFix {
  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.replace.raise.statement");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PyRaiseStatement raiseStatement = (PyRaiseStatement) descriptor.getPsiElement();
    PyExpression[] expressions = raiseStatement.getExpressions();
    assert expressions != null;
    PyElementGenerator elementGenerator = PythonLanguage.getInstance().getElementGenerator();
    String newExpressionText = expressions[0].getText() + "(" + expressions[1].getText() + ")";
    if (expressions.length == 2) {
      raiseStatement.replace(elementGenerator.createFromText(project, PyRaiseStatement.class, "raise " + newExpressionText));
    } else if (expressions.length == 3) {
      raiseStatement.replace(elementGenerator.createFromText(project, PyRaiseStatement.class,
                                                             "raise " + newExpressionText + ".with_traceback(" + expressions[2].getText() + ")"));
    }
  }
}
