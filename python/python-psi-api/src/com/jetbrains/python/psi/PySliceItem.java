// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstSliceItem;
import org.jetbrains.annotations.Nullable;


public interface PySliceItem extends PyAstSliceItem, PyElement {
  @Override
  @Nullable
  default PyExpression getLowerBound() {
    return (PyExpression)PyAstSliceItem.super.getLowerBound();
  }

  @Override
  @Nullable
  default PyExpression getUpperBound() {
    return (PyExpression)PyAstSliceItem.super.getUpperBound();
  }

  @Override
  @Nullable
  default PyExpression getStride() {
    return (PyExpression)PyAstSliceItem.super.getStride();
  }
}
