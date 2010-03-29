package com.jetbrains.python.validation;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.patterns.SyntaxMatchers;

import java.util.List;

/**
 * Highlights incorrect return statements: 'return' and 'yield' outside functions, returning values from generators;
 */
public class ReturnAnnotator extends PyAnnotator {
  public void visitPyReturnStatement(final PyReturnStatement node) {
    List<? extends PsiElement> found = SyntaxMatchers.IN_FUNCTION.search(node);
    if (found == null) {
      getHolder().createErrorAnnotation(node, "'return' outside of function");
      return;
    }
    PyFunction function = (PyFunction)found.get(0);
    if (node.getExpression() != null) {
      YieldVisitor visitor = new YieldVisitor();
      function.acceptChildren(visitor);
      if (visitor.haveYield()) {
        getHolder().createErrorAnnotation(node, "'return' with argument inside generator");
      }
    }

  }

  public void visitPyYieldExpression(final PyYieldExpression node) {
    if (SyntaxMatchers.IN_FUNCTION.search(node) == null) {
      getHolder().createErrorAnnotation(node, "'yield' outside of function");
    }
    /* this is now allowed in python 2.5
    if (node.getContainingElement(PyTryFinallyStatement.class) != null) {
      getHolder().createErrorAnnotation(node, "'yield' not allowed in a 'try' block with a 'finally' clause");
    }
    */
  }


  private static class YieldVisitor extends PyElementVisitor {
    private boolean _haveYield = false;

    public boolean haveYield() {
      return _haveYield;
    }

    @Override
    public void visitPyYieldExpression(final PyYieldExpression node) {
      _haveYield = true;
    }

    @Override
    public void visitPyElement(final PyElement node) {
      if (!_haveYield) {
        node.acceptChildren(this);
      }
    }

    @Override
    public void visitPyFunction(final PyFunction node) {
      // do not go to nested functions
    }
  }
}
