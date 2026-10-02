// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.validation;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyGlobalStatement;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.ParamHelper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Annotates errors in 'global' statements.
 */
@ApiStatus.Internal
public class PyGlobalAnnotatorVisitor extends PyElementVisitor {
  private final @NotNull PyAnnotationHolder myHolder;

  public PyGlobalAnnotatorVisitor(@NotNull PyAnnotationHolder holder) { myHolder = holder; }

  @Override
  public void visitPyGlobalStatement(final @NotNull PyGlobalStatement node) {
    PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class);
    if (function != null) {
      PyParameterList paramList = function.getParameterList();
      // collect param names
      final Set<String> paramNames = new HashSet<>();

      ParamHelper.walkDownParamArray(
        paramList.getParameters(),
        new ParamHelper.ParamVisitor() {
          @Override
          public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
            paramNames.add(param.getName());
          }
        }
      );

      // check globals
      for (PyTargetExpression expr : node.getGlobals()) {
        final String expr_name = expr.getReferencedName();
        if (paramNames.contains(expr_name)) {
          myHolder.newAnnotation(HighlightSeverity.ERROR, PyPsiBundle.message("ANN.name.used.both.as.global.and.param", expr_name))
            .range(expr).create();
        }
      }
    }
  }
}
