// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.validation;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * Marks misplaced default 'except' clauses.
 *
 * @author yole
 */
public class TryExceptAnnotator extends PyAnnotator {
  @Override
  public void visitPyTryExceptStatement(final @NotNull PyTryExceptStatement node) {
    final PyExceptPart[] exceptParts = node.getExceptParts();
    boolean haveDefaultExcept = false;
    for (PyExceptPart part : exceptParts) {
      if (haveDefaultExcept) {
        getHolder().newAnnotation(HighlightSeverity.ERROR, PyBundle.message("ANN.default.except.must.be.last")).range(part).create();
      }
      if (part.getExceptClass() == null) {
        haveDefaultExcept = true;
      }
    }
  }

  @Override
  public void visitPyRaiseStatement(@NotNull PyRaiseStatement node) {
    if (node.getExpressions().length == 0 &&
        PsiTreeUtil.getParentOfType(node, PyExceptPart.class, PyFinallyPart.class, PyFunction.class) == null) {
      markError(node, PyBundle.message("ANN.no.exception.to.reraise"));
    }
  }
}
