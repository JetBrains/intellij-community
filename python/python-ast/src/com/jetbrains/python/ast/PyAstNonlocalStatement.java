// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


@ApiStatus.Experimental
public interface PyAstNonlocalStatement extends PyAstStatement {
  PyAstTargetExpression @NotNull [] getVariables();

  @Override
  default void acceptPyVisitor(PyAstElementVisitor visitor) {
    visitor.visitPyNonlocalStatement(this);
  }
}
