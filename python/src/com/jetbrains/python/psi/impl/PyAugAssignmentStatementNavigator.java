package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import org.jetbrains.annotations.Nullable;

/**
 * @author oleg
 */
public class PyAugAssignmentStatementNavigator {
  private PyAugAssignmentStatementNavigator() {
  }

  @Nullable
  public static PyAugAssignmentStatement getStatementByTarget(final PsiElement element){
    final PyAugAssignmentStatement statement = PsiTreeUtil.getParentOfType(element, PyAugAssignmentStatement.class);
    if (statement == null){
      return null;
    }
    return statement.getTarget() == element ? statement : null;
  }
}
