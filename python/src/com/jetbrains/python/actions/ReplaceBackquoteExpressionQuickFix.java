package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyReprExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: 08.02.2010
 * Time: 20:23:53
 */
public class ReplaceBackquoteExpressionQuickFix implements LocalQuickFix {
  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.replace.backquote.expression");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PyReprExpression problemElement = (PyReprExpression) descriptor.getPsiElement();
    if (problemElement != null && problemElement.getExpression() != null) {
      PyElementGenerator elementGenerator = PythonLanguage.getInstance().getElementGenerator();
      problemElement.replace(elementGenerator.createExpressionFromText(project, "repr(" + problemElement.getExpression().getText() + ")"));
    }
  }
}
