package com.jetbrains.python.validation;

import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyTryExceptStatement;

/**
 * Marks misplaced default 'except' clauses.
 *
 * @author yole
 */
public class TryExceptAnnotator extends PyAnnotator {
  @Override
  public void visitPyTryExceptStatement(final PyTryExceptStatement node) {
    PyExceptPart[] exceptParts = node.getExceptParts();
    boolean haveDefaultExcept = false;
    for (PyExceptPart part : exceptParts) {
      if (haveDefaultExcept) {
        getHolder().createErrorAnnotation(part, PyBundle.message("ANN.default.except.must.be.last"));
      }
      if (part.getExceptClass() == null) {
        haveDefaultExcept = true;
      }
    }
  }
}
