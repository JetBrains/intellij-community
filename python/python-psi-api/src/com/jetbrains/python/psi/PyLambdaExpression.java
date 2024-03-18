// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstLambdaExpression;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface PyLambdaExpression extends PyAstLambdaExpression, PyExpression, PyCallable, ScopeOwner {
  @Override
  @Nullable
  default PyExpression getBody() {
    return (PyExpression)PyAstLambdaExpression.super.getBody();
  }

  @Override
  @NotNull
  default PyParameterList getParameterList() {
    return (PyParameterList)PyAstLambdaExpression.super.getParameterList();
  }

  @Override
  @Nullable
  default PyFunction asMethod() {
    return (PyFunction)PyAstLambdaExpression.super.asMethod();
  }
}
