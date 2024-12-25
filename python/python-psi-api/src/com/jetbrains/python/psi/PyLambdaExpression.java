// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstLambdaExpression;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface PyLambdaExpression extends PyAstLambdaExpression, PyExpression, PyCallable, ScopeOwner {
  @Override
  default @Nullable PyExpression getBody() {
    return (PyExpression)PyAstLambdaExpression.super.getBody();
  }

  @Override
  default @NotNull PyParameterList getParameterList() {
    return (PyParameterList)PyAstLambdaExpression.super.getParameterList();
  }

  @Override
  default @Nullable PyFunction asMethod() {
    return (PyFunction)PyAstLambdaExpression.super.asMethod();
  }
}
