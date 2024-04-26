// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.jetbrains.python.PythonDialectsTokenSetProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


@ApiStatus.Experimental
public interface PyAstSequenceExpression extends PyAstExpression {
  PyAstExpression @NotNull [] getElements();

  /**
   * Calling {@link #getElements()} may take too much time in case of large literals with thousands of elements. If you only need to
   * know whether collection is empty, use this method instead.
   *
   * @return true if sequence expression contains no elements
   */
  default boolean isEmpty() {
    return childToPsi(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens()) == null;
  }
}
