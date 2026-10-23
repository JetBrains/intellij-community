// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.validation;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PySyntaxCoreBundle;
import com.jetbrains.python.ast.PyAstElementVisitor;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.ast.PyAstGlobalStatement;
import com.jetbrains.python.ast.PyAstNamedParameter;
import com.jetbrains.python.ast.PyAstParameterList;
import com.jetbrains.python.ast.PyAstTargetExpression;
import com.jetbrains.python.ast.impl.ParamHelperCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Annotates errors in 'global' statements.
 */
@ApiStatus.Internal
public class PyGlobalAnnotatorVisitor extends PyAstElementVisitor {
  private final @NotNull PyAnnotationHolder myHolder;

  public PyGlobalAnnotatorVisitor(@NotNull PyAnnotationHolder holder) { myHolder = holder; }

  @Override
  public void visitPyGlobalStatement(final @NotNull PyAstGlobalStatement node) {
    PyAstFunction function = PsiTreeUtil.getParentOfType(node, PyAstFunction.class);
    if (function != null) {
      PyAstParameterList paramList = function.getParameterList();
      // collect param names
      final Set<String> paramNames = new HashSet<>();

      ParamHelperCore.walkDownParamArray(
        paramList.getParameters(),
        new ParamHelperCore.ParamVisitor() {
          @Override
          public void visitNamedParameter(PyAstNamedParameter param, boolean first, boolean last) {
            paramNames.add(param.getName());
          }
        }
      );

      // check globals
      for (PyAstTargetExpression expr : node.getGlobals()) {
        final String expr_name = expr.getReferencedName();
        if (paramNames.contains(expr_name)) {
          myHolder.newAnnotation(HighlightSeverity.ERROR, PySyntaxCoreBundle.message("ANN.name.used.both.as.global.and.param", expr_name))
            .range(expr).create();
        }
      }
    }
  }
}
