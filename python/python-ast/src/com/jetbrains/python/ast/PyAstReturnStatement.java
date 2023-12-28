// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.jetbrains.python.PythonDialectsTokenSetProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Experimental
public interface PyAstReturnStatement extends PyAstStatement {
  default @Nullable PyAstExpression getExpression() {
    return childToPsi(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens(), 0);
  }
}
