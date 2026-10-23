// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.validation;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PySyntaxCoreBundle;
import com.jetbrains.python.ast.controlFlow.AstScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtilCore;
import com.jetbrains.python.ast.PyAstClass;
import com.jetbrains.python.ast.PyAstElementVisitor;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.ast.PyAstLambdaExpression;
import com.jetbrains.python.ast.PyAstReturnStatement;
import com.jetbrains.python.ast.PyAstYieldExpression;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Highlights incorrect return statements: 'return' and 'yield' outside functions
 */
@ApiStatus.Internal
public class PyReturnYieldAnnotatorVisitor extends PyAstElementVisitor {
  private final @NotNull PyAnnotationHolder myHolder;

  PyReturnYieldAnnotatorVisitor(@NotNull PyAnnotationHolder holder) { myHolder = holder; }

  @Override
  public void visitPyReturnStatement(final @NotNull PyAstReturnStatement node) {
    final PyAstFunction function = PsiTreeUtil.getParentOfType(node, PyAstFunction.class, false, PyAstClass.class);
    if (function == null) {
      myHolder.newAnnotation(HighlightSeverity.ERROR, PySyntaxCoreBundle.message("ANN.return.outside.of.function")).create();
    }
    if (function != null && node.getExpression() != null && function.isAsync() && function.isAsyncAllowed() && function.isGenerator()) {
      myHolder.newAnnotation(HighlightSeverity.ERROR, PySyntaxCoreBundle.message("ANN.non.empty.return.inside.asynchronous.generator")).create();
    }
  }

  @Override
  public void visitPyYieldExpression(final @NotNull PyAstYieldExpression node) {
    final AstScopeOwner owner = ScopeUtilCore.getScopeOwner(node);
    if (!(owner instanceof PyAstFunction || owner instanceof PyAstLambdaExpression)) {
      myHolder.newAnnotation(HighlightSeverity.ERROR, PySyntaxCoreBundle.message("ANN.yield.outside.of.function")).create();
    }

    if (node.isDelegating() && owner instanceof PyAstFunction function) {

      if (function.isAsync() && function.isAsyncAllowed()) {
        myHolder.newAnnotation(HighlightSeverity.ERROR,
                               PySyntaxCoreBundle.message("ANN.python.does.not.support.yield.from.inside.async.functions")).create();
      }
    }
  }
}
