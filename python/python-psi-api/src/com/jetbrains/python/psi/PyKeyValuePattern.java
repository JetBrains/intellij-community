// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstKeyValuePattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PyKeyValuePattern extends PyAstKeyValuePattern, PyPattern {
  @Override
  default @NotNull PyPattern getKeyPattern() {
    return (PyPattern)PyAstKeyValuePattern.super.getKeyPattern();
  }

  @Override
  default @Nullable PyPattern getValuePattern() {
    return (PyPattern)PyAstKeyValuePattern.super.getValuePattern();
  }
}
