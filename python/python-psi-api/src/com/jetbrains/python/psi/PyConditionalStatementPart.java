// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstConditionalStatementPart;
import org.jetbrains.annotations.Nullable;

/**
 * A statement part that has a condition before it.
 */
public interface PyConditionalStatementPart extends PyAstConditionalStatementPart, PyStatementPart {
  /**
   * @return the condition expression.
   */
  @Override
  default @Nullable PyExpression getCondition() {
    return (PyExpression)PyAstConditionalStatementPart.super.getCondition();
  }
}
