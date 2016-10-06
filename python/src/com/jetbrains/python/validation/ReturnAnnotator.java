/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;

/**
 * Highlights incorrect return statements: 'return' and 'yield' outside functions
 */
public class ReturnAnnotator extends PyAnnotator {
  public void visitPyReturnStatement(final PyReturnStatement node) {
    final PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class, false, PyClass.class);
    if (function == null) {
      getHolder().createErrorAnnotation(node, "'return' outside of function");
    }
    if (function != null && node.getExpression() != null) {
      final PyType returnType = TypeEvalContext.codeAnalysis(function.getProject(), function.getContainingFile()).getReturnType(function);

      if (returnType != null && PyNames.FAKE_ASYNC_GENERATOR.equals(returnType.getName())) {
        getHolder().createErrorAnnotation(node, "non-empty 'return' inside asynchronous generator");
      }
    }
  }

  public void visitPyYieldExpression(final PyYieldExpression node) {
    final ScopeOwner owner = ScopeUtil.getScopeOwner(node);
    if (!(owner instanceof PyFunction || owner instanceof PyLambdaExpression)) {
      getHolder().createErrorAnnotation(node, "'yield' outside of function");
    }

    if (node.isDelegating() && owner instanceof PyFunction) {
      final PyFunction function = (PyFunction)owner;

      if (function.isAsync() && function.isAsyncAllowed()) {
        getHolder().createErrorAnnotation(node, "Python does not support 'yield from' inside async functions");
      }
    }
  }
}
