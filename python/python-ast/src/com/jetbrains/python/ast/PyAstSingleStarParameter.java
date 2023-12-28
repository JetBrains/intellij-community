// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a single star (keyword-only parameter delimiter) appearing in the
 * parameter list of a Py3K function.
 */
@ApiStatus.Experimental
public interface PyAstSingleStarParameter extends PyAstParameter {
  @NonNls String TEXT = "*";

  @Nullable
  @Override
  default PyAstNamedParameter getAsNamed() {
    return null;
  }

  @Nullable
  @Override
  default PyAstTupleParameter getAsTuple() {
    return null;
  }

  @Override
  default PyAstExpression getDefaultValue() {
    return null;
  }

  @Override
  default boolean hasDefaultValue() {
    return false;
  }

  @Nullable
  @Override
  default String getDefaultValueText() {
    return null;
  }
}
