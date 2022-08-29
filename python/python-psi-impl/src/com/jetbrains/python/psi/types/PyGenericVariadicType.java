// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PyGenericVariadicType extends PyGenericType {
  private final boolean myIsHomogeneous;

  @Nullable
  private final List<PyType> myElementTypes;

  public PyGenericVariadicType(@NotNull String name) {
    this(name, false, null, null);
  }

  public PyGenericVariadicType(@NotNull String name, boolean isHomogeneous, @Nullable List<PyType> elementTypes,
                               @Nullable PyQualifiedNameOwner scopeOwner) {
    super(name, null, false, null);
    myElementTypes = elementTypes;
    myIsHomogeneous = isHomogeneous;
  }

  public PyGenericVariadicType(@NotNull String name, boolean isDefinition, @Nullable PyTargetExpression target,
                               boolean isHomogeneous, @Nullable List<PyType> elementTypes, @Nullable PyQualifiedNameOwner scopeOwner) {
    super(name, null, isDefinition, target);
    myElementTypes = elementTypes;
    myIsHomogeneous = isHomogeneous;
    if (scopeOwner != null) {
      setScopeOwner(scopeOwner);
    }
  }

  @NotNull
  @Override
  public PyGenericType withScopeOwner(@Nullable PyQualifiedNameOwner scopeOwner) {
    return new PyGenericVariadicType(myName, isDefinition(), getDeclarationElement(), myIsHomogeneous, myElementTypes, scopeOwner);
  }

  @NotNull
  @Override
  public PyGenericType withTargetExpression(@Nullable PyTargetExpression targetExpression) {
    return new PyGenericVariadicType(myName, isDefinition(), targetExpression, myIsHomogeneous, myElementTypes, getScopeOwner());
  }

  @NotNull
  @Override
  public PyGenericVariadicType withAlias(@Nullable PyTargetExpression alias) {
    return new PyGenericVariadicType(myName, isDefinition(), alias, myIsHomogeneous, myElementTypes, getScopeOwner());
  }

  @NotNull
  public PyGenericVariadicType withDifferentName() {
    return new PyGenericVariadicType(myName + "142", isDefinition(), null, myIsHomogeneous, myElementTypes, getScopeOwner());
  }

  @NotNull
  public PyGenericVariadicType withElementTypes(boolean isHomogeneous, @NotNull List<PyType> elementTypes) {
    var resultElementTypes = new ArrayList<>(elementTypes);
    for (int i = 0; i < elementTypes.size(); ++i) {
      var elementType = elementTypes.get(i);
      if (equals(elementType)) {
        resultElementTypes.set(i, ((PyGenericVariadicType)elementType).withDifferentName());
      }
    }
    return new PyGenericVariadicType(myName, isDefinition(), getDeclarationElement(), isHomogeneous, resultElementTypes, getScopeOwner());
  }

  @NotNull
  @Override
  public PyGenericVariadicType toggleIsDefinition() {
    return new PyGenericVariadicType(myName, !isDefinition(), getDeclarationElement(), myIsHomogeneous, myElementTypes, getScopeOwner());
  }

  @NotNull
  public static PyGenericVariadicType fromElementTypes(@NotNull List<PyType> elementTypes) {
    return new PyGenericVariadicType("", false, elementTypes, null);
  }

  @NotNull
  public static PyGenericVariadicType homogeneous(@Nullable PyType type) {
    var elementTypes = new ArrayList<PyType>();
    elementTypes.add(type);
    return new PyGenericVariadicType("", true, elementTypes, null);
  }

  @NotNull
  @Override
  public String getName() {
    if (myElementTypes == null) {
      return "*" + myName;
    }
    else {
      return "*" + getElementTypesToStr();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final PyGenericVariadicType type = (PyGenericVariadicType)o;
    return myName.equals(type.myName) && isDefinition() == type.isDefinition() && Objects.equals(getScopeOwner(), type.getScopeOwner()) &&
           Objects.equals(myElementTypes, type.myElementTypes);
  }

  @Override
  public int hashCode() {
    int res = myName.hashCode();
    if (myElementTypes == null) return res;
    return res + myElementTypes.hashCode();
  }

  @NotNull
  public String getElementTypesToStr() {
    if (myElementTypes == null) return "";
    StringBuilder res = new StringBuilder();
    res.append("(");
    for (int i = 0; i < myElementTypes.size(); ++i) {
      var type = myElementTypes.get(i);
      var name = type != null ? type.getName() : "Any";
      res.append(name);
      if (i < myElementTypes.size() - 1) {
        res.append(",");
      }
    }
    if (isHomogeneous()) {
      res.append(", ...");
    }
    res.append(")");
    return res.toString();
  }

  public boolean isHomogeneous() {
    return myIsHomogeneous;
  }

  public boolean isMapped(@NotNull Map<PyGenericVariadicType, PyGenericVariadicType> typeVarTuples) {
    if (myIsHomogeneous) return false;
    if (myElementTypes != null && typeVarTuples.containsKey(this)) {
      assert false;
    }
    if (myElementTypes != null) return true;
    return typeVarTuples.containsKey(this) && typeVarTuples.get(this).myElementTypes != null;
  }

  @Nullable
  public List<PyType> getMappedElementTypes(@NotNull Map<PyGenericVariadicType, PyGenericVariadicType> typeVarTuples) {
    if (myIsHomogeneous) return null;
    if (myElementTypes != null && typeVarTuples.containsKey(this)) {
      assert false;
    }
    if (myElementTypes != null) return myElementTypes;

    if (!typeVarTuples.containsKey(this)) return null;
    var mapped = typeVarTuples.get(this);
    if (mapped == null) return null;
    if (mapped.isHomogeneous()) {
      return List.of(mapped);
    }
    if (mapped.myElementTypes == null && !mapped.toString().equals(toString())) {
      return List.of(mapped);
    }
    return mapped.myElementTypes;
  }

  @Nullable
  public PyType getIteratedItemType() {
    if (myElementTypes == null) return null;
    return PyUnionType.union(myElementTypes);
  }
}
