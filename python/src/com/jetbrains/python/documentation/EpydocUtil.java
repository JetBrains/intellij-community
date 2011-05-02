package com.jetbrains.python.documentation;

import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;

/**
 * @author yole
 */
public class EpydocUtil {
  private EpydocUtil() {
  }

  public static boolean isVariableDocString(PyStringLiteralExpression expr) {
    if (PyDocumentationSettings.getInstance(expr.getProject()).isEpydocFormat(expr.getContainingFile())) {
      final PsiElement parent = expr.getParent();
      if (!(parent instanceof PyExpressionStatement)) {
        return false;
      }
      PsiElement prevElement = parent.getPrevSibling();
      while (prevElement instanceof PsiWhiteSpace || prevElement instanceof PsiComment) {
        prevElement = prevElement.getPrevSibling();
      }
      if (prevElement instanceof PyAssignmentStatement) {
        final PyAssignmentStatement assignmentStatement = (PyAssignmentStatement)prevElement;
        final ScopeOwner scope = PsiTreeUtil.getParentOfType(prevElement, ScopeOwner.class);
        if (scope instanceof PyClass || scope instanceof PyFile) {
          return true;
        }
        if (scope instanceof PyFunction) {
          PyFunction function = (PyFunction) scope;
          if (!PyNames.INIT.equals(function.getName())) {
            return false;
          }
          for (PyExpression target : assignmentStatement.getTargets()) {
            if (PyUtil.isInstanceAttribute(target)) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }
}
