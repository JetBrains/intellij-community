// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstKeyValuePattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PyKeyValuePattern extends PyAstKeyValuePattern, PyPattern {
  @Override
  @NotNull
  default PyPattern getKeyPattern() {
    return (PyPattern)PyAstKeyValuePattern.super.getKeyPattern();
  }

  @Override
  @Nullable
  default PyPattern getValuePattern() {
    return (PyPattern)PyAstKeyValuePattern.super.getValuePattern();
  }
}
