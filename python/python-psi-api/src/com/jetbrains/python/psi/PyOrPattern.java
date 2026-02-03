// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstOrPattern;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface PyOrPattern extends PyAstOrPattern, PyPattern {
  @Override
  default @NotNull List<PyPattern> getAlternatives() {
    //noinspection unchecked
    return (List<PyPattern>)PyAstOrPattern.super.getAlternatives();
  }
}
