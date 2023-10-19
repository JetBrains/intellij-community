// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class PyGenericVariadicType implements PyTypeParameterType {
  private final @NotNull String myName;
  private final @Nullable PyQualifiedNameOwner myScopeOwner;
  private final PyTargetExpression myTarget;
  
  private final @Nullable List<PyType> myElementTypes;
  private final boolean myIsHomogeneous;

  public PyGenericVariadicType(@NotNull String name) {
    this(name, false, null);
  }

  public PyGenericVariadicType(@NotNull String name, boolean isHomogeneous, @Nullable List<PyType> elementTypes) {
    this(name, null, isHomogeneous, elementTypes, null);
  }

  private PyGenericVariadicType(@NotNull String name, @Nullable PyTargetExpression target,
                                boolean isHomogeneous, @Nullable List<PyType> elementTypes, @Nullable PyQualifiedNameOwner scopeOwner) {
    myName = name;
    myTarget = target;
    myElementTypes = elementTypes;
    myIsHomogeneous = isHomogeneous;
    myScopeOwner = scopeOwner;
  }

  @NotNull
  public PyGenericVariadicType withScopeOwner(@Nullable PyQualifiedNameOwner scopeOwner) {
    return new PyGenericVariadicType(myName, myTarget, myIsHomogeneous, myElementTypes, scopeOwner);
  }

  public PyGenericVariadicType withTargetExpression(@Nullable PyTargetExpression targetExpression) {
    return new PyGenericVariadicType(myName, targetExpression, myIsHomogeneous, myElementTypes, myScopeOwner);
  }

  @NotNull
  public static PyGenericVariadicType fromElementTypes(@NotNull List<PyType> elementTypes) {
    return new PyGenericVariadicType("", false, elementTypes);
  }

  @NotNull
  public static PyGenericVariadicType homogeneous(@Nullable PyType type) {
    if (type instanceof PyGenericVariadicType) {
      throw new IllegalArgumentException("Unpacked tuple of a TypeVarTuple or another unpacked tuple cannot be constructed");
    }
    var elementTypes = new ArrayList<PyType>();
    elementTypes.add(type);
    return new PyGenericVariadicType("", true, elementTypes);
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
  public @Nullable PyQualifiedNameOwner getScopeOwner() {
    return myScopeOwner;
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
    return myName.equals(type.myName) && Objects.equals(getScopeOwner(), type.getScopeOwner()) &&
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
    StringBuilder res = new StringBuilder("tuple[");
    StringUtil.join(myElementTypes, type -> type != null ? type.getName() : "Any", ", ", res);
    if (isHomogeneous()) {
      res.append(", ...");
    }
    res.append("]");
    return res.toString();
  }

  public boolean isHomogeneous() {
    return isUnpackedTupleType() && myIsHomogeneous;
  }

  public boolean isUnspecified() {
    return isHomogeneous() && myElementTypes != null && myElementTypes.size() == 1 && myElementTypes.get(0) == null;
  }

  @Nullable
  public PyType getIteratedItemType() {
    if (myElementTypes == null) return null;
    return PyUnionType.union(myElementTypes);
  }

  public @Nullable List<PyType> getElementTypes() {
    return myElementTypes != null ? Collections.unmodifiableList(myElementTypes) : null;
  }

  public boolean isUnpackedTupleType() {
    return myName.isEmpty();
  }

  @Override
  public boolean isBuiltin() {
    return false;
  }

  @Override
  public void assertValid(String message) {

  }

  @Override
  public @Nullable List<? extends RatedResolveResult> resolveMember(@NotNull String name,
                                                                    @Nullable PyExpression location,
                                                                    @NotNull AccessDirection direction,
                                                                    @NotNull PyResolveContext resolveContext) {
    return null;
  }

  @Override
  public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public String toString() {
    if (myName.isEmpty()) {
      return "PyGenericVariadicType: " + getElementTypesToStr();
    }
    else {
      String scopeName = myScopeOwner != null ? Objects.requireNonNullElse(myScopeOwner.getQualifiedName(), myScopeOwner.getName()) : null;
      return "PyGenericVariadicType: " + (scopeName != null ? scopeName + ":" : "") + myName;
    }
  }

  public @Nullable PyTupleType asTupleType(@NotNull PsiElement anchor) {
    if (isUnpackedTupleType()) {
      if (isHomogeneous()) {
        return PyTupleType.createHomogeneous(anchor, getElementTypes().get(0));
      }
      else {
        return PyTupleType.create(anchor, getElementTypes());
      }
    }
    else {
      return PyTupleType.create(anchor, Collections.singletonList(this));
    }
  }
}
