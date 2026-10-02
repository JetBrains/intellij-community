// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.validation;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Checks for non-top-level star imports.
 */
@ApiStatus.Internal
public class PyImportAnnotatorVisitor extends PyElementVisitor {
  private final @NotNull PyAnnotationHolder myHolder;

  public PyImportAnnotatorVisitor(@NotNull PyAnnotationHolder holder) { myHolder = holder; }

  @Override
  public void visitPyFromImportStatement(final @NotNull PyFromImportStatement node) {
    if (node.isStarImport() && PsiTreeUtil.getParentOfType(node, PyFunction.class, PyClass.class) != null) {
      myHolder.newAnnotation(HighlightSeverity.WARNING, PyPsiBundle.message("ANN.star.import.at.top.only")).create();
    }
  }
}
