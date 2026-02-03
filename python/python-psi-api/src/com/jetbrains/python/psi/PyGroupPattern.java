// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstGroupPattern;
import org.jetbrains.annotations.NotNull;

public interface PyGroupPattern extends PyAstGroupPattern, PyPattern {
  @Override
  default @NotNull PyPattern getPattern() {
    return (PyPattern)PyAstGroupPattern.super.getPattern();
  }
}
