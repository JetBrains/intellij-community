// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstSliceExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@SuppressWarnings("MissingDeprecatedAnnotation")
@Deprecated(forRemoval = true)
public interface PySliceExpression extends PyAstSliceExpression, PyExpression {
  @Override
  default @NotNull PyExpression getOperand() {
    return (PyExpression)PyAstSliceExpression.super.getOperand();
  }

  @Override
  default @Nullable PySliceItem getSliceItem() {
    return (PySliceItem)PyAstSliceExpression.super.getSliceItem();
  }
}
