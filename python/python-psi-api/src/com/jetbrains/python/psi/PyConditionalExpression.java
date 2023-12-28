// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstConditionalExpression;
import org.jetbrains.annotations.Nullable;


public interface PyConditionalExpression extends PyAstConditionalExpression, PyExpression {
  @Override
  default PyExpression getTruePart() {
    return (PyExpression)PyAstConditionalExpression.super.getTruePart();
  }

  @Override
  @Nullable
  default PyExpression getCondition() {
    return (PyExpression)PyAstConditionalExpression.super.getCondition();
  }

  @Override
  @Nullable
  default PyExpression getFalsePart() {
    return (PyExpression)PyAstConditionalExpression.super.getFalsePart();
  }
}
