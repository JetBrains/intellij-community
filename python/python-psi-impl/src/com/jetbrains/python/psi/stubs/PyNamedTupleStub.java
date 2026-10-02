// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.stubs;

import com.jetbrains.python.psi.impl.stubs.CustomTargetExpressionStub;
import com.jetbrains.python.psi.types.PyAnyType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;

@ApiStatus.Internal
public interface PyNamedTupleStub extends CustomTargetExpressionStub {

  /**
   * @return namedtuple's name.
   */
  @NotNull
  String getName();

  /**
   * @return fields' names and their types.
   * Iteration order repeats the declaration order.
   */
  @NotNull
  LinkedHashMap<String, FieldTypeAndHasDefault> getFields();

  record FieldTypeAndHasDefault(@Nullable String type, boolean hasDefault) {
  }
}
