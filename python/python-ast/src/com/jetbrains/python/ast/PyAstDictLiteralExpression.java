// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.jetbrains.python.PyElementTypes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a literal dict expression, e.g. <tt>{'a': 1}</tt>
 */
@ApiStatus.Experimental
public interface PyAstDictLiteralExpression extends PyAstSequenceExpression {
  @Override
  default PyAstKeyValueExpression @NotNull [] getElements() {
    return childrenToPsi(PyElementTypes.KEY_VALUE_EXPRESSIONS, PyAstKeyValueExpression.EMPTY_ARRAY);
  }

  @Override
  default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
    pyVisitor.visitPyDictLiteralExpression(this);
  }
}
