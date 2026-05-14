// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyCallSiteOwner;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.psi.PyTypeParameterList;
import com.jetbrains.python.psi.PyTypeParameterListOwner;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.ParamHelper;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * Type of particular function that is represented as a {@link PyCallable} in the PSI tree.
 */
public class PyFunctionTypeImpl implements PyFunctionType {

  private final @NotNull PyCallable myCallable;
  private final @NotNull List<@NotNull PyCallableParameter> myParameters;

  public PyFunctionTypeImpl(@NotNull PyCallable callable, @NotNull List<@NotNull PyCallableParameter> parameters) {
    myCallable = callable;
    myParameters = parameters;
  }

  @Override
  public @Nullable List<PyTypeParameterType> getTypeParameters(TypeEvalContext context) {
    if (!(myCallable instanceof PyTypeParameterListOwner owner)) return null;
    PyTypeParameterList typeParameterList = owner.getTypeParameterList();
    if (typeParameterList == null) return null;
    List<PyTypeParameterType> result = new ArrayList<>();
    for (PyTypeParameter typeParameter : typeParameterList.getTypeParameters()) {
      var type = PyTypingTypeProvider.getTypeParameterTypeFromTypeParameter(typeParameter, context);
      if (type != null) {
        result.add(type);
      }
    }
    return result.isEmpty() ? null : result;
  }

  @Override
  public @Nullable PyType getReturnType(@NotNull TypeEvalContext context) {
    return context.getReturnType(myCallable);
  }

  @Override
  public @Nullable PyType getCallType(@NotNull TypeEvalContext context, @NotNull PyCallSiteOwner callSite) {
    return myCallable.getCallType(context, callSite);
  }

  @Override
  public @Nullable List<@NotNull PyCallableParameter> getParameters(@NotNull TypeEvalContext context) {
    return myParameters;
  }

  @Override
  public @Nullable List<PyCallableParameter> getUnpackedParameters(@NotNull TypeEvalContext context) {
    PyCallableParameterListType callableParameterListType = as(getParametersType(context), PyCallableParameterListType.class);
    if (callableParameterListType != null) {
      return callableParameterListType.getUnpackedParameters(context);
    }
    return getParameters(context);
  }

  @Override
  public @Nullable PyCallableParameterVariadicType getParametersType(@NotNull TypeEvalContext context) {
    List<PyCallableParameter> parameters = ContainerUtil.notNullize(getParameters(context));
    return new PyCallableParameterListTypeImpl(parameters);
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
  public Object @NotNull [] getCompletionVariants(String completionPrefix, PsiElement location, @NotNull ProcessingContext context) {
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
    final List<PyCallableParameter> parameters = ContainerUtil.notNullize(getParameters(context));

    List<PyCallableParameter> newParams = ParamHelper.dropSelf(parameters);
    return newParams.size() < parameters.size() ? new PyFunctionTypeImpl(myCallable, newParams) : this;
  }

  @Override
  public String toString() {
    return "PyFunctionType: " + getName();
  }

  @Override
  public <T> T acceptTypeVisitor(@NotNull PyTypeVisitor<T> visitor) {
    return visitor.visitPyFunctionType(this);
  }
}
