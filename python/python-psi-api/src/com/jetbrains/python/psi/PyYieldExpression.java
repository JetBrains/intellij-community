// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstYieldExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface PyYieldExpression extends PyAstYieldExpression, PyExpression {
  @Override
  @Nullable
  default PyExpression getExpression() {
    return (PyExpression)PyAstYieldExpression.super.getExpression();
  }

  /**
   * @return For {@code yield}, returns type of its expression. For {@code yield from} - YieldType of the delegate 
   */
  @Nullable
  PyType getYieldType(@NotNull TypeEvalContext context);

  /**
   * @return If containing function is annotated with Generator (or AsyncGenerator), returns SendType from annotation.
   * Otherwise, Any for {@code yield} and SendType of the delegate for {@code yield from}
   */
  @Nullable
  PyType getSendType(@NotNull TypeEvalContext context);
}
