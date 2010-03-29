package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.Nullable;

/**
 * @author oleg
 */
public class PyAssignmentStatementNavigator {
  private PyAssignmentStatementNavigator() {
  }

  @Nullable
  public static PyAssignmentStatement getStatementByTarget(final PsiElement element){
    final PyAssignmentStatement parent = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement.class);
    if (parent != null){
      for (PyExpression expression : parent.getTargets()) {
        if (element == expression || element.getParent() == expression){
          return parent;
        }
      }
    }
    return null;
  }
}
