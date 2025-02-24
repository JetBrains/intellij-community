package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.Ref;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class PyTypeVarTypeImpl extends PyGenericType {
  public PyTypeVarTypeImpl(@NotNull String name, @Nullable PyType bound) {
    this(name, List.of(), bound, null);
  }

  public PyTypeVarTypeImpl(@NotNull String name,
                           @NotNull List<@Nullable PyType> constraints,
                           @Nullable PyType bound,
                           @Nullable Ref<PyType> defaultType) {
    super(name, constraints, bound, defaultType);
  }

  PyTypeVarTypeImpl(@NotNull String name,
                    @NotNull List<@Nullable PyType> constraints,
                    @Nullable PyType bound,
                    @Nullable Ref<PyType> defaultType,
                    boolean isDefinition,
                    @Nullable PyQualifiedNameOwner declarationElement,
                    @Nullable PyQualifiedNameOwner scopeOwner) {
    super(name, constraints, bound, defaultType, isDefinition, declarationElement, scopeOwner);
  }

  @Override
  public <T> T acceptTypeVisitor(@NotNull PyTypeVisitor<T> visitor) {
    return visitor.visitPyTypeVarType(this);
  }
}
