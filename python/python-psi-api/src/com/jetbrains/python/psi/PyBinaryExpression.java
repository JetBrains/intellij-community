// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstBinaryExpression;
import com.jetbrains.python.ast.PyAstExpression;
import org.jetbrains.annotations.Nullable;


public interface PyBinaryExpression extends PyAstBinaryExpression, PyQualifiedExpression, PyCallSiteExpression, PyReferenceOwner {

  @Override
  default PyExpression getLeftExpression() {
    return (PyExpression)PyAstBinaryExpression.super.getLeftExpression();
  }

  @Override
  default @Nullable PyExpression getRightExpression() {
    return (PyExpression)PyAstBinaryExpression.super.getRightExpression();
  }

  /**
   * @deprecated Use {@link PyAstBinaryExpression#getOppositeExpression(PyAstExpression)}
   */
  @SuppressWarnings("MethodOverloadsMethodOfSuperclass")
  @Deprecated
  default PyExpression getOppositeExpression(PyExpression expression)
    throws IllegalArgumentException {
    return (PyExpression)PyAstBinaryExpression.super.getOppositeExpression(expression);
  }

  @Override
  default @Nullable PyExpression getQualifier() {
    return (PyExpression)PyAstBinaryExpression.super.getQualifier();
  }
}
