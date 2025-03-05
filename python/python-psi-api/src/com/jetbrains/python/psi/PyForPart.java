// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstForPart;
import org.jetbrains.annotations.Nullable;

/**
 * Main part of a 'for' statement
 */
public interface PyForPart extends PyAstForPart, PyStatementPart {
  /**
   * @return target: the "x" in "{@code for x in (1, 2, 3)}".
   */
  @Override
  default @Nullable PyExpression getTarget() {
    return (PyExpression)PyAstForPart.super.getTarget();
  }

  /**
   * @return source of iteration: the "(1, 2, 3)" in "{@code for x in (1, 2, 3)}".
   */
  @Override
  default @Nullable PyExpression getSource() {
    return (PyExpression)PyAstForPart.super.getSource();
  }

}
