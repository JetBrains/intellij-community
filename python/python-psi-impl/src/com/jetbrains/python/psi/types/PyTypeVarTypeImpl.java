package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PyTypeVarTypeImpl extends PyGenericType {
  public PyTypeVarTypeImpl(@NotNull String name, @Nullable PyType bound) {
    super(name, bound);
  }
  public PyTypeVarTypeImpl(@NotNull String name, @Nullable PyType bound, @Nullable Ref<PyType> defaultType) {
    super(name, bound, defaultType);
  }
}
