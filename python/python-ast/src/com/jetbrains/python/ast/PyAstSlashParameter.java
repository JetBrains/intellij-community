// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * Represents positional-only parameters delimiter introduced in Python 3.8 (PEP 570).
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface PyAstSlashParameter extends PyAstParameter {
  @NonNls String TEXT = "/";

  @Override
  default PyAstNamedParameter getAsNamed() {
    return null;
  }

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
