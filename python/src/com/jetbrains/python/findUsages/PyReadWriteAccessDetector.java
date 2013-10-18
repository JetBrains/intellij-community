package com.jetbrains.python.findUsages;

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyDelStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyAugAssignmentStatementNavigator;

/**
 * @author yole
 */
public class PyReadWriteAccessDetector extends ReadWriteAccessDetector {
  @Override
  public boolean isReadWriteAccessible(PsiElement element) {
    return element instanceof PyTargetExpression || element instanceof PyReferenceExpression;
  }

  @Override
  public boolean isDeclarationWriteAccess(PsiElement element) {
    return element instanceof PyTargetExpression || element.getParent() instanceof PyDelStatement;
  }

  @Override
  public Access getReferenceAccess(PsiElement referencedElement, PsiReference reference) {
    return getExpressionAccess(reference.getElement());
  }

  @Override
  public Access getExpressionAccess(PsiElement expression) {
    if (isDeclarationWriteAccess(expression)) {
      return Access.Write;
    }
    if (expression instanceof PyReferenceExpression) {
      final PyAugAssignmentStatement statement = PyAugAssignmentStatementNavigator.getStatementByTarget(expression);
      if (statement != null) {
        return Access.ReadWrite;
      }
    }
    return Access.Read;
  }
}
