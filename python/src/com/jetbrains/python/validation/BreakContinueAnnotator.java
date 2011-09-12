package com.jetbrains.python.validation;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.patterns.SyntaxMatchers;

import java.util.List;

import static com.jetbrains.python.PyBundle.message;

/**
 * Annotates misplaced 'break' and 'continue'.
 */
public class BreakContinueAnnotator extends PyAnnotator {
  @Override
  public void visitPyBreakStatement(final PyBreakStatement node) {
    if (SyntaxMatchers.LOOP_CONTROL.search(node) == null) {
      getHolder().createErrorAnnotation(node, message("ANN.break.outside.loop"));
    }
  }

  @Override
  public void visitPyContinueStatement(final PyContinueStatement node) {
    List<? extends PsiElement> match = SyntaxMatchers.LOOP_CONTROL.search(node);
    if (match == null) {
      getHolder().createErrorAnnotation(node, message("ANN.continue.outside.loop"));
    }
    else if (PsiTreeUtil.getParentOfType(node,  PyFinallyPart.class, false, PyForStatement.class, PyWhileStatement.class) != null) {
      getHolder().createErrorAnnotation(node, message("ANN.cant.continue.in.finally"));
    }
  }
}