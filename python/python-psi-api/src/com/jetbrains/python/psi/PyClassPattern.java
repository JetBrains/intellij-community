// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstClassPattern;
import org.jetbrains.annotations.NotNull;

public interface PyClassPattern extends PyAstClassPattern, PyPattern {
  @Override
  @NotNull
  default PyReferenceExpression getClassNameReference() {
    return (PyReferenceExpression)PyAstClassPattern.super.getClassNameReference();
  }

  @Override
  @NotNull
  default PyPatternArgumentList getArgumentList() {
    return (PyPatternArgumentList)PyAstClassPattern.super.getArgumentList();
  }
}
