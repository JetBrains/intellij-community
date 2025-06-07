// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.jetbrains.python.PythonDialectsTokenSetProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Experimental
public interface PyAstSliceItem extends PyAstExpression {
  default @Nullable PyAstExpression getLowerBound() {
    return childToPsi(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens(), 0);
  }

  default @Nullable PyAstExpression getUpperBound() {
    return childToPsi(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens(), 1);
  }

  default @Nullable PyAstExpression getStride() {
    return childToPsi(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens(), 2);
  }
}
