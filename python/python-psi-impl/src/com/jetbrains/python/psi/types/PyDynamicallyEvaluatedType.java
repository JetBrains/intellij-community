// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.jetbrains.python.PyNames;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;

@ApiStatus.Internal
public final class PyDynamicallyEvaluatedType extends PyUnionType {
  private PyDynamicallyEvaluatedType(@NotNull LinkedHashSet<@Nullable PyType> members) {
    super(members);
  }

  public static @NotNull PyDynamicallyEvaluatedType create(@NotNull PyType type) {
    final LinkedHashSet<PyType> members = new LinkedHashSet<>();
    if (type instanceof PyUnionType unionType) {
      members.addAll(unionType.getMembers());
    }
    else {
      members.add(type);
    }
    members.add(PyAnyType.getUnknown());
    return new PyDynamicallyEvaluatedType(members);
  }

  @Override
  public String getName() {
    PyType res = excludeNull();
    return res != null ? res.getName() : PyNames.ANY_TYPE;
  }
}
