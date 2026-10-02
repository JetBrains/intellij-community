// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.validation;

import com.intellij.lang.annotation.HighlightSeverity;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyTypeDeclarationStatement;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
@ApiStatus.Internal
public class PyTypeAnnotationTargetAnnotatorVisitor extends PyElementVisitor {
  private final @NotNull PyAnnotationHolder myHolder;

  public PyTypeAnnotationTargetAnnotatorVisitor(@NotNull PyAnnotationHolder holder) { myHolder = holder; }

  @Override
  public void visitPyAssignmentStatement(@NotNull PyAssignmentStatement node) {
    if (node.getAnnotation() != null && LanguageLevel.forElement(node).isAtLeast(LanguageLevel.PYTHON36)) {
      if (node.getRawTargets().length > 1) {
        myHolder.newAnnotation(HighlightSeverity.ERROR,
                               PyPsiBundle.message("ANN.variable.annotation.cannot.be.used.in.assignment.with.multiple.targets")).create();
      }
      final PyExpression target = node.getLeftHandSideExpression();
      if (target != null) {
        checkAnnotationTarget(target);
      }
    }
  }

  @Override
  public void visitPyTypeDeclarationStatement(@NotNull PyTypeDeclarationStatement node) {
    if (node.getAnnotation() != null && LanguageLevel.forElement(node).isAtLeast(LanguageLevel.PYTHON36)) {
      checkAnnotationTarget(node.getTarget());
    }
  }

  private void checkAnnotationTarget(@NotNull PyExpression expression) {
    final PyExpression innerExpr = PyPsiUtils.flattenParens(expression);
    if (innerExpr instanceof PyTupleExpression || innerExpr instanceof PyListLiteralExpression) {
      myHolder.newAnnotation(HighlightSeverity.ERROR,
                             PyPsiBundle.message("ANN.variable.annotation.cannot.be.combined.with.tuple.unpacking")).range(innerExpr)
        .create();
    }
    else if (innerExpr != null && !(innerExpr instanceof PyTargetExpression || innerExpr instanceof PySubscriptionExpression)) {
      myHolder.newAnnotation(HighlightSeverity.ERROR, PyPsiBundle.message("ANN.illegal.target.for.variable.annotation")).range(innerExpr)
        .create();
    }
  }
}
