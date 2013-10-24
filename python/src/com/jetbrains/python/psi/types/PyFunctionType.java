/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Type of a particular function that is represented as a {@link Callable} in the PSI tree.
 *
 * @author vlan
 */
public class PyFunctionType implements PyCallableType {
  @NotNull private final Callable myCallable;

  public PyFunctionType(@NotNull Callable callable) {
    myCallable = callable;
  }

  @Override
  public boolean isCallable() {
    return true;
  }

  @Nullable
  @Override
  public PyType getCallType(@NotNull TypeEvalContext context, @Nullable PyQualifiedExpression callSite) {
    return myCallable.getReturnType(context, callSite);
  }

  @Nullable
  @Override
  public List<PyCallableParameter> getParameters(@NotNull TypeEvalContext context) {
    final List<PyCallableParameter> result = new ArrayList<PyCallableParameter>();
    for (PyParameter parameter : myCallable.getParameterList().getParameters()) {
      result.add(new PyCallableParameterImpl(parameter));
    }
    return result;
  }

  @Override
  public List<? extends RatedResolveResult> resolveMember(@NotNull String name,
                                                          @Nullable PyExpression location,
                                                          @NotNull AccessDirection direction,
                                                          @NotNull PyResolveContext resolveContext) {
    return Collections.emptyList();
  }

  @Override
  public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
    return new Object[0];
  }

  @Override
  public String getName() {
    return "function";
  }

  @Override
  public boolean isBuiltin(TypeEvalContext context) {
    return false;
  }

  @Override
  public void assertValid(String message) {
  }

  @NotNull
  public Callable getCallable() {
    return myCallable;
  }

  @Nullable
  public static String getParameterName(@NotNull PyNamedParameter namedParameter) {
    String name = namedParameter.getName();
    if (namedParameter.isPositionalContainer()) {
      name = "*" + name;
    }
    else if (namedParameter.isKeywordContainer()) {
      name = "**" + name;
    }
    return name;
  }
}
