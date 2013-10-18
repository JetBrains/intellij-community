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
    final PyAssignmentStatement assignmentStatement = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement.class);
    if (assignmentStatement != null){
      for (PyExpression expression : assignmentStatement.getTargets()) {
        if (element == expression){
          return assignmentStatement;
        }
        final PsiElement parent = element.getParent();
        if (parent == expression && parent.getFirstChild() == element && parent.getLastChild() == element){
          return assignmentStatement;
        }
      }
    }
    return null;
  }
}
