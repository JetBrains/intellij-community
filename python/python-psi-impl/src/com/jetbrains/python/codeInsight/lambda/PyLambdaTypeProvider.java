// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.lambda;

import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

public class PyLambdaTypeProvider extends PyTypeProviderBase {
  @Override
  public PyType getCallableType(@NotNull PyCallable callable, @NotNull TypeEvalContext context) {
    if (callable instanceof PyLambdaExpression lambdaExpression) {
      var parent = lambdaExpression.getParent();
      if (parent instanceof PyArgumentList argumentList) {
        var callExpression = argumentList.getCallExpression();
        if (callExpression != null) {
          var callee = callExpression.getCallee();
          if (callee != null) {
            int argumentIndex = 0;
            for (var expression : argumentList.getArguments()) {
              if (expression == lambdaExpression) {
                break;
              }
              argumentIndex++;
            }
            if (argumentIndex > argumentList.getArguments().length) {
              return null;
            }
            if (callee instanceof PyReferenceExpression referenceExpression) {
              var reference = referenceExpression.getReference().resolve();
              if (reference instanceof PyFunction function) {
                if (function.getModifier() != PyAstFunction.Modifier.STATICMETHOD && function.getContainingClass() != null) {
                  argumentIndex++;
                }
                var functionParameters = function.getParameters(context);
                if (argumentIndex < functionParameters.size()) {
                  return functionParameters.get(argumentIndex).getType(context);
                }
              }
            }
          }
        }
      }
    }
    return null;
  }
}
