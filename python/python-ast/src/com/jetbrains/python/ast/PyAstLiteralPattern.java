// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.python.ast.PyAstElementKt.findNotNullChildByClass;

@ApiStatus.Experimental
public interface PyAstLiteralPattern extends PyAstPattern {
  default @NotNull PyAstExpression getExpression() {
    return findNotNullChildByClass(this, PyAstExpression.class);
  }

  @Override
  default boolean isIrrefutable() {
    return false;
  }
}
