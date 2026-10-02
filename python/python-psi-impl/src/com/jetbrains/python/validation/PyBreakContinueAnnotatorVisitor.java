// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.validation;

import com.intellij.lang.annotation.HighlightSeverity;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.PyBreakStatement;
import com.jetbrains.python.psi.PyContinueStatement;
import com.jetbrains.python.psi.PyElementVisitor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Annotates misplaced 'break' and 'continue'.
 */
@ApiStatus.Internal
public class PyBreakContinueAnnotatorVisitor extends PyElementVisitor {
  private final @NotNull PyAnnotationHolder myHolder;

  public PyBreakContinueAnnotatorVisitor(@NotNull PyAnnotationHolder holder) { myHolder = holder; }

  @Override
  public void visitPyBreakStatement(final @NotNull PyBreakStatement node) {
    if (node.getLoopStatement() == null) {
      myHolder.newAnnotation(HighlightSeverity.ERROR, PyPsiBundle.message("ANN.break.outside.loop")).create();
    }
  }

  @Override
  public void visitPyContinueStatement(final @NotNull PyContinueStatement node) {
    if (node.getLoopStatement() == null) {
      myHolder.newAnnotation(HighlightSeverity.ERROR, PyPsiBundle.message("ANN.continue.outside.loop")).create();
    }
  }
}
