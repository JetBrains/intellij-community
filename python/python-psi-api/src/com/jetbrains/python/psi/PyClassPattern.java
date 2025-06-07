// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstClassPattern;
import org.jetbrains.annotations.NotNull;

public interface PyClassPattern extends PyAstClassPattern, PyPattern {
  @Override
  default @NotNull PyReferenceExpression getClassNameReference() {
    return (PyReferenceExpression)PyAstClassPattern.super.getClassNameReference();
  }

  @Override
  default @NotNull PyPatternArgumentList getArgumentList() {
    return (PyPatternArgumentList)PyAstClassPattern.super.getArgumentList();
  }
}
