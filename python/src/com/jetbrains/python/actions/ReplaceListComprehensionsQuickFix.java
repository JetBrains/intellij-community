package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: 12.02.2010
 * Time: 18:15:24
 */
public class ReplaceListComprehensionsQuickFix implements LocalQuickFix {
  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.replace.list.comprehensions");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PyExpression expression = (PyExpression) descriptor.getPsiElement();
    PyElementGenerator elementGenerator = PythonLanguage.getInstance().getElementGenerator();
    expression.replace(elementGenerator.createFromText(project, PyParenthesizedExpression.class, "(" + expression.getText() + ")"));
  }
}
