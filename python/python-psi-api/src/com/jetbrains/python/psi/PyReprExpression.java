// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstReprExpression;
import org.jetbrains.annotations.Nullable;


public interface PyReprExpression extends PyAstReprExpression, PyExpression {
  @Override
  @Nullable
  default PyExpression getExpression() {
    return (PyExpression)PyAstReprExpression.super.getExpression();
  }
}
