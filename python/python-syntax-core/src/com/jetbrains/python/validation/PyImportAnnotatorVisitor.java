// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.validation;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PySyntaxCoreBundle;
import com.jetbrains.python.ast.PyAstClass;
import com.jetbrains.python.ast.PyAstElementVisitor;
import com.jetbrains.python.ast.PyAstFromImportStatement;
import com.jetbrains.python.ast.PyAstFunction;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Checks for non-top-level star imports.
 */
@ApiStatus.Internal
public class PyImportAnnotatorVisitor extends PyAstElementVisitor {
  private final @NotNull PyAnnotationHolder myHolder;

  public PyImportAnnotatorVisitor(@NotNull PyAnnotationHolder holder) { myHolder = holder; }

  @Override
  public void visitPyFromImportStatement(final @NotNull PyAstFromImportStatement node) {
    if (node.isStarImport() && PsiTreeUtil.getParentOfType(node, PyAstFunction.class, PyAstClass.class) != null) {
      myHolder.newAnnotation(HighlightSeverity.WARNING, PySyntaxCoreBundle.message("ANN.star.import.at.top.only")).create();
    }
  }
}
