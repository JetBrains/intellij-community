// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstSliceItem;
import org.jetbrains.annotations.Nullable;


public interface PySliceItem extends PyAstSliceItem, PyExpression {
  @Override
  default @Nullable PyExpression getLowerBound() {
    return (PyExpression)PyAstSliceItem.super.getLowerBound();
  }

  @Override
  default @Nullable PyExpression getUpperBound() {
    return (PyExpression)PyAstSliceItem.super.getUpperBound();
  }

  @Override
  default @Nullable PyExpression getStride() {
    return (PyExpression)PyAstSliceItem.super.getStride();
  }
}
