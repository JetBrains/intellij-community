// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstSingleStarPattern;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

import static com.jetbrains.python.ast.PyAstElementKt.findChildByClass;

public interface PySingleStarPattern extends PyAstSingleStarPattern, PyPattern {
  @NotNull
  default PyPattern getPattern() {
    return Objects.requireNonNull(findChildByClass(this, PyPattern.class));
  }

  @NotNull List<@Nullable PyType> getCapturedTypesFromSequenceType(@Nullable PyType sequenceType, @NotNull TypeEvalContext context);
}
