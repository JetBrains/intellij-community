// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstPrefixExpression;
import org.jetbrains.annotations.Nullable;


public interface PyPrefixExpression extends PyAstPrefixExpression, PyQualifiedExpression, PyReferenceOwner, PyCallSiteExpression {
  @Override
  default @Nullable PyExpression getOperand() {
    return (PyExpression)PyAstPrefixExpression.super.getOperand();
  }

  @Override
  default @Nullable PyExpression getQualifier() {
    return (PyExpression)PyAstPrefixExpression.super.getQualifier();
  }
}
