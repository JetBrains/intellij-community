package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * User: catherine
 *
 * QuickFix to remove redundant parentheses from if/while/except statement
 */
public class RedundantParenthesesQuickFix implements LocalQuickFix {

  public RedundantParenthesesQuickFix() {
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.redundant.parentheses");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    if (element != null && element.isWritable()) {
      while (element instanceof PyParenthesizedExpression) {
        PyExpression expression = ((PyParenthesizedExpression)element).getContainedExpression();
        if (expression != null) {
          element = element.replace(expression);
        }
      }
    }
  }
}
