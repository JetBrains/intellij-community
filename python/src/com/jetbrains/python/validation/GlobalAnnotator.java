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

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ParamHelper;

import java.util.HashSet;
import java.util.Set;

/**
 * Annotates errors in 'global' statements.
 */
public class GlobalAnnotator extends PyAnnotator {
  @Override
  public void visitPyGlobalStatement(final PyGlobalStatement node) {
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
      final AnnotationHolder holder = getHolder();
      for (PyTargetExpression expr : node.getGlobals()) {
        final String expr_name = expr.getReferencedName();
        if (paramNames.contains(expr_name)) {
          holder.createErrorAnnotation(expr.getTextRange(), PyBundle.message("ANN.$0.both.global.and.param", expr_name));
        }
      }
    }
  }
}
