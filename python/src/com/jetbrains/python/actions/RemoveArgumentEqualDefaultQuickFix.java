package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * User: catherine
 *
 * QuickFix to remove redundant argument equal default
 */
public class RemoveArgumentEqualDefaultQuickFix implements LocalQuickFix {
  Set<PyExpression> myProblemElements;
  public RemoveArgumentEqualDefaultQuickFix(Set<PyExpression> problemElements) {
    myProblemElements = problemElements;
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.remove.argument.equal.default");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    PyExpression[] arguments = PsiTreeUtil.getParentOfType(element, PyArgumentList.class).getArguments();
    boolean canDelete = true;
    for (int i = arguments.length-1; i != -1; --i) {
      if (myProblemElements.contains(arguments[i])) {
        if (canDelete)
          arguments[i].delete();
      }
      else
        canDelete = false;
    }
  }
}
