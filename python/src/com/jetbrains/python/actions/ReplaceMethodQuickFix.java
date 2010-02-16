package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: 10.02.2010
 * Time: 15:46:03
 */
public class ReplaceMethodQuickFix implements LocalQuickFix {
  private final String myNewName;

  public ReplaceMethodQuickFix(String newName) {
    myNewName = newName;
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.replace.method");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PyExpression problemElement = (PyExpression) descriptor.getPsiElement();
    if (problemElement != null) {
      PyElementGenerator elementGenerator = PythonLanguage.getInstance().getElementGenerator();
      problemElement.replace(elementGenerator.createCallExpression(project, myNewName).getCallee());
    }
  }
}
