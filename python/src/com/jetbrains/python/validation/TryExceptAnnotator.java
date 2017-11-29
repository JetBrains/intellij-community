// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.validation;

import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyFinallyPart;
import com.jetbrains.python.psi.PyRaiseStatement;
import com.jetbrains.python.psi.PyTryExceptStatement;

/**
 * Marks misplaced default 'except' clauses.
 *
 * @author yole
 */
public class TryExceptAnnotator extends PyAnnotator {
  @Override
  public void visitPyTryExceptStatement(final PyTryExceptStatement node) {
    final PyExceptPart[] exceptParts = node.getExceptParts();
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

  @Override
  public void visitPyRaiseStatement(PyRaiseStatement node) {
    if (node.getExpressions().length == 0 && PsiTreeUtil.getParentOfType(node, PyExceptPart.class, PyFinallyPart.class) == null) {
      markError(node, "No exception to reraise");
    }
  }
}
