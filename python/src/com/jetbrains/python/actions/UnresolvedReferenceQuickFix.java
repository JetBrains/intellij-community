package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * QuickFix to remove redundant parentheses from if/while/except statement
 */
public class UnresolvedReferenceQuickFix implements LocalQuickFix {

  public UnresolvedReferenceQuickFix() {
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.unresolved.reference");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    if (element != null && element.isWritable()) {
      if (element instanceof PyReferenceExpression) {
        PyReferenceExpression ref = (PyReferenceExpression)element;
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        PyExpression expression = elementGenerator.createExpressionFromText("self." + ref.getText());
        element = element.replace(expression);
      }
    }
  }
}
