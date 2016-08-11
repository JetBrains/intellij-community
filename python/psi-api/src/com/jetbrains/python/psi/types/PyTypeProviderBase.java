/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.FactoryMap;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author yole
 */
public class PyTypeProviderBase implements PyTypeProvider {

  private final ReturnTypeCallback mySelfTypeCallback = (callSite, qualifierType, context) -> Optional
    .ofNullable(ObjectUtils.tryCast(qualifierType, PyClassType.class))
    .map(PyClassType::getPyClass)
    .map(pyClass -> PyPsiFacade.getInstance(pyClass.getProject()).createClassType(pyClass, false))
    .orElse(null);

  @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
  private final Map<String, ReturnTypeDescriptor> myMethodToReturnTypeMap = new FactoryMap<String, ReturnTypeDescriptor>() {
    @Override
    protected ReturnTypeDescriptor create(String key) {
      return new ReturnTypeDescriptor();
    }
  };

  @Nullable
  @Override
  public PyType getReferenceExpressionType(@NotNull PyReferenceExpression referenceExpression, @NotNull TypeEvalContext context) {
    return null;
  }

  @Override
  public PyType getReferenceType(@NotNull PsiElement referenceTarget, TypeEvalContext context, @Nullable PsiElement anchor) {
    return null;
  }

  @Override
  public Ref<PyType> getParameterType(@NotNull PyNamedParameter param, @NotNull PyFunction func, @NotNull TypeEvalContext context) {
    return null;
  }

  @Nullable
  @Override
  public Ref<PyType> getReturnType(@NotNull PyCallable callable, @NotNull TypeEvalContext context) {
    return null;
  }

  @Nullable
  @Override
  public Ref<PyType> getCallType(@NotNull PyFunction function, @Nullable PyCallSiteExpression callSite, @NotNull TypeEvalContext context) {
    final ReturnTypeDescriptor descriptor;
    synchronized (myMethodToReturnTypeMap) {
      descriptor = myMethodToReturnTypeMap.get(function.getName());
    }
    if (descriptor != null) {
      return descriptor.get(function, callSite, context);
    }
    return null;
  }

  @Nullable
  @Override
  public PyType getContextManagerVariableType(PyClass contextManager, PyExpression withExpression, TypeEvalContext context) {
    return null;
  }

  @Nullable
  @Override
  public PyType getCallableType(@NotNull PyCallable callable, @NotNull TypeEvalContext context) {
    return null;
  }

  protected void registerSelfReturnType(@NotNull String classQualifiedName, @NotNull Collection<String> methods) {
    registerReturnType(classQualifiedName, methods, mySelfTypeCallback);
  }

  protected void registerReturnType(@NotNull String classQualifiedName,
                                    @NotNull Collection<String> methods,
                                    @NotNull ReturnTypeCallback callback) {
    synchronized (myMethodToReturnTypeMap) {
      for (String method : methods) {
        myMethodToReturnTypeMap.get(method).put(classQualifiedName, callback);
      }
    }
  }

  protected interface ReturnTypeCallback {

    @Nullable
    PyType getType(@Nullable PyCallSiteExpression callSite, @Nullable PyType qualifierType, @NotNull TypeEvalContext context);
  }

  private static class ReturnTypeDescriptor {

    private final Map<String, ReturnTypeCallback> myStringToReturnTypeMap = new HashMap<>();

    public void put(@NotNull String classQualifiedName, @NotNull ReturnTypeCallback callback) {
      myStringToReturnTypeMap.put(classQualifiedName, callback);
    }

    @Nullable
    public Ref<PyType> get(@NotNull PyFunction function, @Nullable PyCallSiteExpression callSite, @NotNull TypeEvalContext context) {
      return Optional
        .ofNullable(function.getContainingClass())
        .map(pyClass -> myStringToReturnTypeMap.get(pyClass.getQualifiedName()))
        .map(typeCallback -> typeCallback.getType(callSite, getQualifierType(callSite, context), context))
        .map(Ref::create)
        .orElse(null);
    }

    @Nullable
    private static PyType getQualifierType(@Nullable PyCallSiteExpression callSite, @NotNull TypeEvalContext context) {
      final PyExpression callee = callSite instanceof PyCallExpression ? ((PyCallExpression)callSite).getCallee() : null;
      final PyExpression qualifier = callee instanceof PyQualifiedExpression ? ((PyQualifiedExpression)callee).getQualifier() : null;

      return qualifier != null ? context.getType(qualifier) : null;
    }
  }
}
