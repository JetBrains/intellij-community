// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * @deprecated Use {@link PyTypeVarType} and {@link PyTypeVarTypeImpl}  instead.
 * See <a href="https://youtrack.jetbrains.com/issue/PY-59241">PY-59241</a> for the reasoning and transition plan.
 */
@Deprecated(forRemoval = true)
public class PyGenericType implements PyTypeVarType {
  private final @NotNull String myName;
  private final @NotNull List<@Nullable PyType> myConstraints;
  private final @Nullable PyType myBound;
  private final @Nullable Ref<PyType> myDefaultType;
  private final @NotNull Variance myVariance;
  private final boolean myIsDefinition;
  private final @Nullable PyQualifiedNameOwner myDeclarationElement;
  private final @Nullable PyQualifiedNameOwner myScopeOwner;

  public PyGenericType(@NotNull String name,
                       @NotNull List<@Nullable PyType> constraints,
                       @Nullable PyType bound,
                       @Nullable Ref<PyType> defaultType,
                       @NotNull Variance variance) {
    this(name, constraints, bound, defaultType, variance, false, null, null);
  }

  protected PyGenericType(@NotNull String name,
                          @NotNull List<@Nullable PyType> constraints,
                          @Nullable PyType bound,
                          @Nullable Ref<PyType> defaultType,
                          @NotNull Variance variance,
                          boolean isDefinition,
                          @Nullable PyQualifiedNameOwner declarationElement,
                          @Nullable PyQualifiedNameOwner scopeOwner) {
    myName = name;
    myConstraints = constraints;
    myBound = bound;
    myDefaultType = defaultType;
    myVariance = variance;
    myIsDefinition = isDefinition;
    myDeclarationElement = declarationElement;
    myScopeOwner = scopeOwner;
  }

  @Override
  public @Nullable PyQualifiedNameOwner getDeclarationElement() {
    return myDeclarationElement;
  }

  @Override
  public @Nullable List<? extends RatedResolveResult> resolveMember(@NotNull String name,
                                                                    @Nullable PyExpression location,
                                                                    @NotNull AccessDirection direction,
                                                                    @NotNull PyResolveContext resolveContext) {
    PyType bound = getBoundPromotedToClassObjectTypesIfNeeded();
    if (bound != null) {
      return bound.resolveMember(name, location, direction, resolveContext);
    }
    PyType defaultType = getDefaultTypePromotedToClassObjectTypesIfNeeded();
    if (defaultType != null) {
      return defaultType.resolveMember(name, location, direction, resolveContext);
    }
    return null;
  }

  @Override
  public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
    PyType bound = getBoundPromotedToClassObjectTypesIfNeeded();
    if (bound != null) {
      return bound.getCompletionVariants(completionPrefix, location, context);
    }

    PyType defaultType = getDefaultTypePromotedToClassObjectTypesIfNeeded();
    if (defaultType != null) {
      return defaultType.getCompletionVariants(completionPrefix, location, context);
    }
    return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
  }

  private @Nullable PyType getBoundPromotedToClassObjectTypesIfNeeded() {
    PyType effectiveBound = myConstraints.isEmpty() ? myBound : PyUnionType.union(myConstraints);
    return promoteToClassObjectIfNeeded(effectiveBound);
  }

  private @Nullable PyType getDefaultTypePromotedToClassObjectTypesIfNeeded() {
    PyType defaultType = myDefaultType != null ? myDefaultType.get() : null;
    return promoteToClassObjectIfNeeded(defaultType);
  }

  private @Nullable PyType promoteToClassObjectIfNeeded(PyType type) {
    if (myIsDefinition) {
      return PyTypeUtil.toStream(type)
        .map(t -> t instanceof PyInstantiableType ? ((PyInstantiableType<?>)t).toClass() : t)
        .collect(PyTypeUtil.toUnion(type));
    }
    return type;
  }

  @Override
  public @NotNull String getName() {
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

  @Override
  public @NotNull String toString() {
    // A qualified name can be null e.g. for a local function
    String scopeName = myScopeOwner != null ? Objects.requireNonNullElse(myScopeOwner.getQualifiedName(), myScopeOwner.getName()) : null;
    return "PyGenericType: " + (scopeName != null ? scopeName + ":" : "") + getName();
  }

  @Override
  public @NotNull List<@Nullable PyType> getConstraints() {
    return myConstraints;
  }

  @Override
  public @Nullable PyType getBound() {
    return myBound;
  }

  @Override
  public @NotNull Variance getVariance() {
    return myVariance;
  }

  @Override
  public @Nullable Ref<PyType> getDefaultType() {
    return myDefaultType;
  }

  @Override
  public boolean isDefinition() {
    return myIsDefinition;
  }

  @Override
  public @Nullable PyQualifiedNameOwner getScopeOwner() {
    return myScopeOwner;
  }

  public @NotNull PyGenericType withScopeOwner(@Nullable PyQualifiedNameOwner scopeOwner) {
    return new PyTypeVarTypeImpl(getName(), getConstraints(), getBound(), getDefaultType(), getVariance(), isDefinition(), getDeclarationElement(), scopeOwner);
  }

  public @NotNull PyGenericType withTargetExpression(@Nullable PyTargetExpression targetExpression) {
    return withDeclarationElement(targetExpression);
  }

  public @NotNull PyGenericType withDeclarationElement(@Nullable PyQualifiedNameOwner declarationElement) {
    return new PyTypeVarTypeImpl(getName(), getConstraints(), getBound(), getDefaultType(), getVariance(), isDefinition(), declarationElement, getScopeOwner());
  }

  @Override
  public @NotNull PyGenericType toInstance() {
    return myIsDefinition ? new PyTypeVarTypeImpl(myName, myConstraints, myBound, myDefaultType, myVariance, false, myDeclarationElement, myScopeOwner) : this;
  }

  @Override
  public @NotNull PyGenericType toClass() {
    return myIsDefinition ? this : new PyTypeVarTypeImpl(myName, myConstraints, myBound, myDefaultType, myVariance, true, myDeclarationElement, myScopeOwner);
  }
}
