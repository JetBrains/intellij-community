// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Type of a particular function that is represented as a {@link PyCallable} in the PSI tree.
 */
public class PyFunctionTypeImpl implements PyFunctionType {
  public static PyFunctionType create(@NotNull PyCallable callable, @NotNull TypeEvalContext context) {
    List<PyCallableParameter> parameters = new ArrayList<>();
    for (PyParameter parameter : callable.getParameterList().getParameters()) {
      if (parameter instanceof PyNamedParameter namedParameter &&
          namedParameter.isKeywordContainer() &&
          context.getType(namedParameter) instanceof PyTypedDictType typedDictType) {
        List<PyCallableParameter> typedDictParameters = typedDictType.toClass().getParameters(context);
        parameters.addAll(ContainerUtil.notNullize(typedDictParameters));
      }
      else {
        parameters.add(PyCallableParameterImpl.psi(parameter));
      }
    }
    return new PyFunctionTypeImpl(callable, parameters);
  }

  private final @NotNull PyCallable myCallable;
  private final @NotNull List<@NotNull PyCallableParameter> myParameters;

  /**
   * @deprecated Use {@link PyFunctionTypeImpl#create(PyCallable, TypeEvalContext)}
   */
  @Deprecated
  public PyFunctionTypeImpl(@NotNull PyCallable callable) {
    this(callable, ContainerUtil.map(callable.getParameterList().getParameters(), PyCallableParameterImpl::psi));
  }

  public PyFunctionTypeImpl(@NotNull PyCallable callable, @NotNull List<@NotNull PyCallableParameter> parameters) {
    myCallable = callable;
    myParameters = parameters;
  }

  @Override
  public @Nullable PyType getReturnType(@NotNull TypeEvalContext context) {
    return context.getReturnType(myCallable);
  }

  @Override
  public @Nullable PyType getCallType(@NotNull TypeEvalContext context, @NotNull PyCallSiteExpression callSite) {
    return myCallable.getCallType(context, callSite);
  }

  @Override
  public @Nullable List<@NotNull PyCallableParameter> getParameters(@NotNull TypeEvalContext context) {
    return myParameters;
  }

  @Override
  public @Nullable List<? extends RatedResolveResult> resolveMember(@NotNull String name,
                                                                    @Nullable PyExpression location,
                                                                    @NotNull AccessDirection direction,
                                                                    @NotNull PyResolveContext resolveContext) {
    PyClassType delegate = PyUtil.selectCallableTypeRuntimeClass(this, location, resolveContext.getTypeEvalContext());
    return delegate != null ? delegate.resolveMember(name, location, direction, resolveContext) : Collections.emptyList();
  }

  @SuppressWarnings("DuplicatedCode")
  @Override
  public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
    TypeEvalContext typeEvalContext = TypeEvalContext.codeCompletion(location.getProject(), location.getContainingFile());
    PyExpression callee = location instanceof PyReferenceExpression re ? re.getQualifier() : null;
    PyClassType delegate = PyUtil.selectCallableTypeRuntimeClass(this, callee, typeEvalContext);
    return delegate != null ? delegate.getCompletionVariants(completionPrefix, location, context) : ArrayUtilRt.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public boolean isBuiltin() {
    return false;
  }

  @Override
  public void assertValid(String message) {
  }

  @Override
  public @NotNull PyCallable getCallable() {
    return myCallable;
  }

  @Override
  public @NotNull PyFunctionType dropSelf(@NotNull TypeEvalContext context) {
    final List<PyCallableParameter> parameters = getParameters(context);

    if (!ContainerUtil.isEmpty(parameters) && parameters.get(0).isSelf()) {
      return new PyFunctionTypeImpl(myCallable, ContainerUtil.subList(parameters, 1));
    }
    return this;
  }

  @Override
  public String toString() {
    return "PyFunctionType";
  }

  @Override
  public <T> T acceptTypeVisitor(@NotNull PyTypeVisitor<T> visitor) {
    return visitor.visitPyFunctionType(this);
  }
}
