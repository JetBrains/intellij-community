// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.validation;

import com.intellij.lang.annotation.HighlightSeverity;
import com.jetbrains.python.PySyntaxCoreBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.ast.PyAstAssignmentStatement;
import com.jetbrains.python.ast.PyAstElementVisitor;
import com.jetbrains.python.ast.PyAstExpression;
import com.jetbrains.python.ast.PyAstListLiteralExpression;
import com.jetbrains.python.ast.PyAstSubscriptionExpression;
import com.jetbrains.python.ast.PyAstTargetExpression;
import com.jetbrains.python.ast.PyAstTupleExpression;
import com.jetbrains.python.ast.PyAstTypeDeclarationStatement;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
@ApiStatus.Internal
public class PyTypeAnnotationTargetAnnotatorVisitor extends PyAstElementVisitor {
  private final @NotNull PyAnnotationHolder myHolder;

  public PyTypeAnnotationTargetAnnotatorVisitor(@NotNull PyAnnotationHolder holder) { myHolder = holder; }

  @Override
  public void visitPyAssignmentStatement(@NotNull PyAstAssignmentStatement node) {
    if (node.getAnnotation() != null && LanguageLevel.forElement(node).isAtLeast(LanguageLevel.PYTHON36)) {
      if (node.getRawTargets().length > 1) {
        myHolder.newAnnotation(HighlightSeverity.ERROR,
                               PySyntaxCoreBundle.message("ANN.variable.annotation.cannot.be.used.in.assignment.with.multiple.targets")).create();
      }
      final PyAstExpression target = node.getLeftHandSideExpression();
      if (target != null) {
        checkAnnotationTarget(target);
      }
    }
  }

  @Override
  public void visitPyTypeDeclarationStatement(@NotNull PyAstTypeDeclarationStatement node) {
    if (node.getAnnotation() != null && LanguageLevel.forElement(node).isAtLeast(LanguageLevel.PYTHON36)) {
      checkAnnotationTarget(node.getTarget());
    }
  }

  private void checkAnnotationTarget(@NotNull PyAstExpression expression) {
    final PyAstExpression innerExpr = PyPsiUtilsCore.flattenParens(expression);
    if (innerExpr instanceof PyAstTupleExpression || innerExpr instanceof PyAstListLiteralExpression) {
      myHolder.newAnnotation(HighlightSeverity.ERROR,
                             PySyntaxCoreBundle.message("ANN.variable.annotation.cannot.be.combined.with.tuple.unpacking")).range(innerExpr)
        .create();
    }
    else if (innerExpr != null && !(innerExpr instanceof PyAstTargetExpression || innerExpr instanceof PyAstSubscriptionExpression)) {
      myHolder.newAnnotation(HighlightSeverity.ERROR, PySyntaxCoreBundle.message("ANN.illegal.target.for.variable.annotation")).range(innerExpr)
        .create();
    }
  }
}
