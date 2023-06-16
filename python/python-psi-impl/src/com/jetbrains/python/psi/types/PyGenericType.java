// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * @deprecated Use {@link PyTypeVarType} and {@link PyTypeVarTypeImpl}  instead.
 * See <a href="https://youtrack.jetbrains.com/issue/PY-59241">PY-59241</a> for the reasoning and transition plan.
 */
@Deprecated
public class PyGenericType implements PyTypeVarType {
  @NotNull private final String myName;
  @Nullable private final PyType myBound;
  private final boolean myIsDefinition;
  @Nullable private final PyTargetExpression myTargetExpression;
  @Nullable private PyQualifiedNameOwner myScopeOwner;

  public PyGenericType(@NotNull String name, @Nullable PyType bound) {
    this(name, bound, false);
  }

  public PyGenericType(@NotNull String name, @Nullable PyType bound, boolean isDefinition) {
    this(name, bound, isDefinition, null);
  }

  private PyGenericType(@NotNull String name, @Nullable PyType bound, boolean isDefinition, @Nullable PyTargetExpression target) {
    this(name, bound, isDefinition, target, null);
  }

  private PyGenericType(@NotNull String name,
                       @Nullable PyType bound,
                       boolean isDefinition,
                       @Nullable PyTargetExpression target,
                       @Nullable PyQualifiedNameOwner scopeOwner) {
    myName = name;
    myBound = bound;
    myIsDefinition = isDefinition;
    myTargetExpression = target;
    myScopeOwner = scopeOwner;
  }

  @Nullable
  @Override
  public PyTargetExpression getDeclarationElement() {
    return myTargetExpression;
  }

  @Nullable
  @Override
  public List<? extends RatedResolveResult> resolveMember(@NotNull String name,
                                                          @Nullable PyExpression location,
                                                          @NotNull AccessDirection direction,
                                                          @NotNull PyResolveContext resolveContext) {
    PyType bound = getBoundPromotedToClassObjectTypesIfNeeded();
    if (bound != null) {
      return bound.resolveMember(name, location, direction, resolveContext);
    }
    return null;
  }

  @Override
  public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
    PyType bound = getBoundPromotedToClassObjectTypesIfNeeded();
    if (bound != null) {
      return bound.getCompletionVariants(completionPrefix, location, context);
    }
    return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
  }

  @Nullable
  private PyType getBoundPromotedToClassObjectTypesIfNeeded() {
    if (myIsDefinition) {
      return PyTypeUtil.toStream(myBound)
        .map(t -> t instanceof PyInstantiableType ? ((PyInstantiableType<?>)t).toClass() : t)
        .collect(PyTypeUtil.toUnion());
    }
    return myBound;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public boolean isBuiltin() {
    return false;
  }

  @Override
  public void assertValid(String message) {
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final PyGenericType type = (PyGenericType)o;
    return myName.equals(type.myName) && myIsDefinition == type.isDefinition() && Objects.equals(myScopeOwner, type.myScopeOwner);
  }

  @Override
  public int hashCode() {
    return myName.hashCode();
  }

  @NotNull
  @Override
  public String toString() {
    // A qualified name can be null e.g. for a local function
    String scopeName = myScopeOwner != null ? Objects.requireNonNullElse(myScopeOwner.getQualifiedName(), myScopeOwner.getName()) : null;
    return "PyGenericType: " + (scopeName != null ? scopeName + ":" : "") + getName();
  }

  @Override
  public @Nullable PyType getBound() {
    return myBound;
  }

  @Override
  public boolean isDefinition() {
    return myIsDefinition;
  }

  @Override
  public @Nullable PyQualifiedNameOwner getScopeOwner() {
    return myScopeOwner;
  }

  @NotNull
  public PyGenericType withScopeOwner(@Nullable PyQualifiedNameOwner scopeOwner) {
    return new PyGenericType(getName(), getBound(), isDefinition(), getDeclarationElement(), scopeOwner);
  }

  @NotNull
  public PyGenericType withTargetExpression(@Nullable PyTargetExpression targetExpression) {
    return new PyGenericType(getName(), getBound(), isDefinition(), targetExpression, getScopeOwner());
  }

  @ApiStatus.Internal
  public void setScopeOwner(@NotNull PyQualifiedNameOwner scopeOwner) {
    if (myScopeOwner != null && myScopeOwner != scopeOwner) {
      throw new IllegalStateException("Cannot override the existing scope owner");
    }
    myScopeOwner = scopeOwner;
  }

  @NotNull
  @Override
  public PyGenericType toInstance() {
    return myIsDefinition ? new PyGenericType(myName, myBound, false, myTargetExpression, myScopeOwner) : this;
  }

  @NotNull
  @Override
  public PyGenericType toClass() {
    return myIsDefinition ? this : new PyGenericType(myName, myBound, true, myTargetExpression, myScopeOwner);
  }
}
