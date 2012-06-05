package com.jetbrains.python.validation;

import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;

/**
 * Highlights incorrect return statements: 'return' and 'yield' outside functions, returning values from generators;
 */
public class ReturnAnnotator extends PyAnnotator {
  public void visitPyReturnStatement(final PyReturnStatement node) {
    PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class, false, PyClass.class);
    if (function == null) {
      getHolder().createErrorAnnotation(node, "'return' outside of function");
      return;
    }
  }

  public void visitPyYieldExpression(final PyYieldExpression node) {
    if (PsiTreeUtil.getParentOfType(node, PyFunction.class, false, PyClass.class) == null) {
      getHolder().createErrorAnnotation(node, "'yield' outside of function");
    }
    /* this is now allowed in python 2.5
    if (node.getContainingElement(PyTryFinallyStatement.class) != null) {
      getHolder().createErrorAnnotation(node, "'yield' not allowed in a 'try' block with a 'finally' clause");
    }
    */
  }
}
