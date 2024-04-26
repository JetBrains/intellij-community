// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstSliceExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface PySliceExpression extends PyAstSliceExpression, PyExpression {
  @Override
  @NotNull
  default PyExpression getOperand() {
    return (PyExpression)PyAstSliceExpression.super.getOperand();
  }

  @Override
  @Nullable
  default PySliceItem getSliceItem() {
    return (PySliceItem)PyAstSliceExpression.super.getSliceItem();
  }
}
