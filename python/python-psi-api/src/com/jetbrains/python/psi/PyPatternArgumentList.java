// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstPatternArgumentList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface PyPatternArgumentList extends PyAstPatternArgumentList, PyElement {
  @Override
  default @NotNull List<PyPattern> getPatterns() {
    //noinspection unchecked
    return (List<PyPattern>)PyAstPatternArgumentList.super.getPatterns();
  }
}
