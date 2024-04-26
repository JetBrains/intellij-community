// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstOrPattern;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface PyOrPattern extends PyAstOrPattern, PyPattern {
  @Override
  @NotNull
  default List<PyPattern> getAlternatives() {
    //noinspection unchecked
    return (List<PyPattern>)PyAstOrPattern.super.getAlternatives();
  }
}
