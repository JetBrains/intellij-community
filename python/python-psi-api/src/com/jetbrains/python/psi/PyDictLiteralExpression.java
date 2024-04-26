// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstDictLiteralExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a literal dict expression, e.g. <tt>{'a': 1}</tt>
 */
public interface PyDictLiteralExpression extends PyAstDictLiteralExpression, PySequenceExpression {
  @Override
  PyKeyValueExpression @NotNull [] getElements();
}
