package com.jetbrains.python.validation;

import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.PyBundle.message;

/**
 * Annotates misplaced 'break' and 'continue'.
 */
public class BreakContinueAnnotator extends PyAnnotator {
  @Override
  public void visitPyBreakStatement(final PyBreakStatement node) {
    if (getContainingLoop(node) == null) {
      getHolder().createErrorAnnotation(node, message("ANN.break.outside.loop"));
    }
  }

  @Nullable
  private static PyLoopStatement getContainingLoop(PyStatement node) {
    return PsiTreeUtil.getParentOfType(node, PyLoopStatement.class, false, PyFunction.class, PyClass.class);
  }

  @Override
  public void visitPyContinueStatement(final PyContinueStatement node) {
    if (getContainingLoop(node) == null) {
      getHolder().createErrorAnnotation(node, message("ANN.continue.outside.loop"));
    }
    else if (PsiTreeUtil.getParentOfType(node,  PyFinallyPart.class, false, PyLoopStatement.class) != null) {
      getHolder().createErrorAnnotation(node, message("ANN.cant.continue.in.finally"));
    }
  }
}