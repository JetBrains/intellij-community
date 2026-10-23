// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.validation;

import com.intellij.lang.annotation.HighlightSeverity;
import com.jetbrains.python.PySyntaxCoreBundle;
import com.jetbrains.python.ast.PyAstBreakStatement;
import com.jetbrains.python.ast.PyAstContinueStatement;
import com.jetbrains.python.ast.PyAstElementVisitor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Annotates misplaced 'break' and 'continue'.
 */
@ApiStatus.Internal
public class PyBreakContinueAnnotatorVisitor extends PyAstElementVisitor {
  private final @NotNull PyAnnotationHolder myHolder;

  public PyBreakContinueAnnotatorVisitor(@NotNull PyAnnotationHolder holder) { myHolder = holder; }

  @Override
  public void visitPyBreakStatement(final @NotNull PyAstBreakStatement node) {
    if (node.getLoopStatement() == null) {
      myHolder.newAnnotation(HighlightSeverity.ERROR, PySyntaxCoreBundle.message("ANN.break.outside.loop")).create();
    }
  }

  @Override
  public void visitPyContinueStatement(final @NotNull PyAstContinueStatement node) {
    if (node.getLoopStatement() == null) {
      myHolder.newAnnotation(HighlightSeverity.ERROR, PySyntaxCoreBundle.message("ANN.continue.outside.loop")).create();
    }
  }
}
