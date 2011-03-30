package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * QuickFix to replace true with True, false with False
 */
public class UnresolvedRefTrueFalseQuickFix implements LocalQuickFix {
  PsiElement myElement;
  String newName;
  public UnresolvedRefTrueFalseQuickFix(PsiElement element) {
    myElement = element;
    char[] charArray = element.getText().toCharArray();
    charArray[0] = Character.toUpperCase(charArray[0]);
    newName = new String(charArray);
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.unresolved.reference.replace.$0", newName);
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

    PyExpression expression = elementGenerator.createExpressionFromText(newName);
    myElement.replace(expression);
  }
}
