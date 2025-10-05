/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.validation;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ParamHelper;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Annotates errors in 'global' statements.
 */
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
          myHolder.newAnnotation(HighlightSeverity.ERROR, PyPsiBundle.message("ANN.name.used.both.as.global.and.param", expr_name)).range(expr).create();
        }
      }
    }
  }
}
