// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author vlan
 */
public class PyGenericType implements PyType, PyInstantiableType<PyGenericType> {
  @NotNull private final String myName;
  @Nullable private final PyType myBound;
  private final boolean myIsDefinition;
  @Nullable private final PyTargetExpression myTargetExpression;

  public PyGenericType(@NotNull String name, @Nullable PyType bound) {
    this(name, bound, false);
  }

  public PyGenericType(@NotNull String name, @Nullable PyType bound, boolean isDefinition) {
    this(name, bound, isDefinition, null);
  }

  public PyGenericType(@NotNull String name, @Nullable PyType bound, boolean isDefinition, @Nullable PyTargetExpression target) {
    myName = name;
    myBound = bound;
    myIsDefinition = isDefinition;
    myTargetExpression = target;
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
    return myName.equals(type.myName) && myIsDefinition == type.isDefinition();
  }

  @Override
  public int hashCode() {
    return myName.hashCode();
  }

  @NotNull
  @Override
  public String toString() {
    return "PyGenericType: " + getName();
  }

  @Nullable
  public PyType getBound() {
    return myBound;
  }

  @Override
  public boolean isDefinition() {
    return myIsDefinition;
  }

  @NotNull
  @Override
  public PyGenericType toInstance() {
    return myIsDefinition ? new PyGenericType(myName, myBound, false, myTargetExpression) : this;
  }

  @NotNull
  @Override
  public PyGenericType toClass() {
    return myIsDefinition ? this : new PyGenericType(myName, myBound, true, myTargetExpression);
  }
}
