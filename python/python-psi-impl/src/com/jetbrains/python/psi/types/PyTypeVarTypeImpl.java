package com.jetbrains.python.psi.types;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PyTypeVarTypeImpl extends PyGenericType {
  public PyTypeVarTypeImpl(@NotNull String name, @Nullable PyType bound) {
    super(name, bound);
  }
}
