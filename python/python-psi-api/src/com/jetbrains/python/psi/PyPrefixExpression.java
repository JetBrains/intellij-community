// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstPrefixExpression;
import org.jetbrains.annotations.Nullable;


public interface PyPrefixExpression extends PyAstPrefixExpression, PyQualifiedExpression, PyReferenceOwner, PyCallSiteExpression {
  @Override
  @Nullable
  default PyExpression getOperand() {
    return (PyExpression)PyAstPrefixExpression.super.getOperand();
  }

  @Override
  @Nullable
  default PyExpression getQualifier() {
    return (PyExpression)PyAstPrefixExpression.super.getQualifier();
  }
}
