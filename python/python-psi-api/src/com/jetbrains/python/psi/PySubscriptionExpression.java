// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstSubscriptionExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface PySubscriptionExpression extends PyAstSubscriptionExpression, PyQualifiedExpression, PyCallSiteExpression, PyReferenceOwner {

  /**
   * @return For {@code spam[x][y][n]} will return {@code spam} regardless number of its dimensions
   */
  @Override
  default @NotNull PyExpression getRootOperand() {
    return (PyExpression)PyAstSubscriptionExpression.super.getRootOperand();
  }

  @Override
  default @NotNull PyExpression getOperand() {
    return (PyExpression)PyAstSubscriptionExpression.super.getOperand();
  }

  @Override
  default @Nullable PyExpression getIndexExpression() {
    return (PyExpression)PyAstSubscriptionExpression.super.getIndexExpression();
  }

  @Override
  default @Nullable PyExpression getQualifier() {
    return (PyExpression)PyAstSubscriptionExpression.super.getQualifier();
  }
}
