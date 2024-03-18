// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstSubscriptionExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface PySubscriptionExpression extends PyAstSubscriptionExpression, PyQualifiedExpression, PyCallSiteExpression, PyReferenceOwner {

  /**
   * @return For {@code spam[x][y][n]} will return {@code spam} regardless number of its dimensions
   */
  @Override
  @NotNull
  default PyExpression getRootOperand() {
    return (PyExpression)PyAstSubscriptionExpression.super.getRootOperand();
  }

  @Override
  @NotNull
  default PyExpression getOperand() {
    return (PyExpression)PyAstSubscriptionExpression.super.getOperand();
  }

  @Override
  @Nullable
  default PyExpression getIndexExpression() {
    return (PyExpression)PyAstSubscriptionExpression.super.getIndexExpression();
  }

  @Override
  @Nullable
  default PyExpression getQualifier() {
    return (PyExpression)PyAstSubscriptionExpression.super.getQualifier();
  }
}
