// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstWithItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface PyWithItem extends PyAstWithItem, PyElement {
  PyWithItem[] EMPTY_ARRAY = new PyWithItem[0];

  @Override
  @NotNull
  default PyExpression getExpression() {
    return (PyExpression)PyAstWithItem.super.getExpression();
  }

  @Override
  @Nullable
  default PyExpression getTarget() {
    return (PyExpression)PyAstWithItem.super.getTarget();
  }
}
