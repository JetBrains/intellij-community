/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
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
  private boolean myIsDefinition = false;
  private final PyTargetExpression myTargetExpression;

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
    return null;
  }

  @Override
  public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
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
