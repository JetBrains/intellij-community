// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.jetbrains.python.psi.PyQualifiedNameOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class PyTypeVarTupleTypeImpl implements PyTypeVarTupleType {
  private final @NotNull String myName;
  private final @Nullable PyQualifiedNameOwner myScopeOwner;
  private final @Nullable PyType myDefaultType;
  private final @Nullable PyQualifiedNameOwner myDeclarationElement;

  public PyTypeVarTupleTypeImpl(@NotNull String name) {
    this(name, null, null, null);
  }

  private PyTypeVarTupleTypeImpl(@NotNull String name, @Nullable PyQualifiedNameOwner declarationElement, @Nullable PyType defaultType, @Nullable PyQualifiedNameOwner scopeOwner) {
    myName = name;
    myDeclarationElement = declarationElement;
    myDefaultType = defaultType;
    myScopeOwner = scopeOwner;
  }

  @NotNull
  public PyTypeVarTupleTypeImpl withScopeOwner(@Nullable PyQualifiedNameOwner scopeOwner) {
    return new PyTypeVarTupleTypeImpl(myName, myDeclarationElement, myDefaultType, scopeOwner);
  }

  @NotNull
  public PyTypeVarTupleTypeImpl withDeclarationElement(@Nullable PyQualifiedNameOwner declarationElement) {
    return new PyTypeVarTupleTypeImpl(myName, declarationElement, myDefaultType, myScopeOwner);
  }

  @NotNull
  public PyTypeVarTupleTypeImpl withDefaultType(@Nullable PyType defaultType) {
    return new PyTypeVarTupleTypeImpl(myName, myDeclarationElement, defaultType, myScopeOwner);
  }

  @NotNull
  @Override
  public String getName() {
    return "*" + myName;
  }

  @Override
  public @Nullable PyQualifiedNameOwner getScopeOwner() {
    return myScopeOwner;
  }

  @Override
  public @Nullable PyType getDefaultType() {
    return myDefaultType;
  }

  @Override
  public @Nullable PyQualifiedNameOwner getDeclarationElement() {
    return myDeclarationElement;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final PyTypeVarTupleTypeImpl type = (PyTypeVarTupleTypeImpl)o;
    return myName.equals(type.myName) && Objects.equals(getScopeOwner(), type.getScopeOwner());
  }

  @Override
  public int hashCode() {
    return Objects.hash(myName, myScopeOwner);
  }

  @Override
  public String toString() {
    String scopeName = myScopeOwner != null ? Objects.requireNonNullElse(myScopeOwner.getQualifiedName(), myScopeOwner.getName()) : null;
    return "PyGenericVariadicType: " + (scopeName != null ? scopeName + ":" : "") + myName;
  }
}
