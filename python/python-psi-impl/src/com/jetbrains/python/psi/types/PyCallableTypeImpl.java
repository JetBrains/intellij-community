// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyCallSiteOwner;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.ArgumentMappingResults;
import com.jetbrains.python.psi.impl.ParamHelper;
import com.jetbrains.python.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class PyCallableTypeImpl implements PyCallableType {
  private final @Nullable List<PyTypeParameterType> myTypeParameters;
  private final @Nullable PyCallableParameterVariadicType myParametersType;
  private final @Nullable PyType myReturnType;
  private final @Nullable PyCallable myCallable;
  private final @Nullable PyFunction.Modifier myModifier;

  public PyCallableTypeImpl(@Nullable PyCallableParameterVariadicType parametersType, @Nullable PyType returnType) {
    this(null, parametersType, returnType, null, null);
  }

  public PyCallableTypeImpl(@Nullable List<PyTypeParameterType> typeParameters,
                            @Nullable PyCallableParameterVariadicType parametersType,
                            @Nullable PyType returnType,
                            @Nullable PyCallable callable,
                            @Nullable PyFunction.Modifier modifier) {
    myTypeParameters = typeParameters;
    myParametersType = parametersType;
    PyAnyType.validate(returnType);
    myReturnType = returnType;
    myCallable = callable;
    myModifier = modifier;
  }

  public PyCallableTypeImpl(@Nullable List<PyCallableParameter> parameters, @Nullable PyType returnType) {
    this(null, parameters != null ? new PyCallableParameterListTypeImpl(parameters) : null, returnType, null, null);
  }

  public PyCallableTypeImpl(@Nullable List<PyCallableParameter> parameters,
                            @Nullable PyType returnType,
                            @Nullable PyCallable callable,
                            @Nullable PyFunction.Modifier modifier) {
    this(null, parameters != null ? new PyCallableParameterListTypeImpl(parameters) : null, returnType, callable, modifier);
  }

  @Override
  public @Nullable List<PyTypeParameterType> getTypeParameters(TypeEvalContext context) {
    return myTypeParameters;
  }

  @Override
  public @Nullable PyType getReturnType(@NotNull TypeEvalContext context) {
    return myReturnType;
  }

  @Override
  public @Nullable PyType getCallType(@NotNull TypeEvalContext context, @NotNull PyCallSiteOwner callSite) {
    if (myCallable instanceof PyFunction function) {
      for (PyTypeProvider typeProvider : PyTypeProvider.EP_NAME.getExtensionList()) {
        final Ref<PyType> typeRef = typeProvider.getCallType(function, callSite, context);
        if (typeRef != null) {
          return typeRef.get();
        }
      }
    }

    if (!PyTypeChecker.hasGenerics(myReturnType, context)) {
      return PyNarrowedType.Companion.bindIfNeeded(myReturnType, callSite);
    }

    List<PyExpression> arguments = callSite.getArguments(myCallable);
    PyCallableParameterListType parametersType = new PyCallableParameterListTypeImpl(ContainerUtil.notNullize(getParameters(context)));
    ArgumentMappingResults mappingResults = PyCallExpressionHelper.analyzeArguments(arguments, parametersType, context);
    final var substitutions = PyTypeChecker.unifyGenericCall(null, mappingResults.getMappedParameters(), context);
    final var substitutionsWithUnresolvedReturnGenerics =
      PyTypeChecker.getSubstitutionsWithUnresolvedReturnGenerics(this, myReturnType, substitutions, context);
    PyType typeAfterSubstitution = PyTypeChecker.substitute(myReturnType, substitutionsWithUnresolvedReturnGenerics, context);
    return PyNarrowedType.Companion.bindIfNeeded(typeAfterSubstitution, callSite);
  }

  @Override
  public @Nullable List<PyCallableParameter> getParameters(@NotNull TypeEvalContext context) {
    if (myParametersType instanceof PyCallableParameterListType parameterListType) {
      return parameterListType.getParameters();
    }
    // For backward compatibility with code that expects a single parameter wrapping ParamSpec/Concatenate
    if (myParametersType instanceof PyParamSpecType || myParametersType instanceof PyConcatenateType) {
      return Collections.singletonList(PyCallableParameterImpl.nonPsi(myParametersType));
    }
    return null;
  }

  @Override
  public @Nullable List<PyCallableParameter> getUnpackedParameters(@NotNull TypeEvalContext context) {
    List<PyCallableParameter> parameters = getParameters(context);
    if (parameters == null) return null;
    return ParamHelper.unpackContainerParameters(parameters, context);
  }

  @Override
  public @Nullable PyCallableParameterVariadicType getParametersType(@NotNull TypeEvalContext context) {
    return myParametersType;
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
  public String toString() {
    return "PyCallableType: " + getName();
  }

  @Override
  public boolean isBuiltin() {
    return false;
  }

  @Override
  public void assertValid(String message) {
  }

  @Override
  public @Nullable PyCallable getCallable() {
    return myCallable;
  }

  @Override
  public @Nullable PyFunction.Modifier getModifier() {
    return myModifier;
  }

  @Override
  public @Nullable PyQualifiedNameOwner getDeclarationElement() {
    return myCallable;
  }

  @Override
  public @NotNull PyCallableType dropSelf(@NotNull TypeEvalContext context) {
    if (!(myParametersType instanceof PyCallableParameterListType)) {
      return this;
    }

    final List<PyCallableParameter> parameters = getParameters(context);
    if (ContainerUtil.isEmpty(parameters)) return this;

    if (parameters.getFirst().isSelf()) {
      List<PyCallableParameter> newParameters = ContainerUtil.subList(parameters, 1);
      return new PyCallableTypeImpl(myTypeParameters,
                                    new PyCallableParameterListTypeImpl(newParameters),
                                    myReturnType, myCallable, myModifier);
    }

    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PyCallableTypeImpl type = (PyCallableTypeImpl)o;
    return Objects.equals(myTypeParameters, type.myTypeParameters) &&
           Objects.equals(myParametersType, type.myParametersType) &&
           Objects.equals(myReturnType, type.myReturnType) &&
           Objects.equals(myCallable, type.myCallable) &&
           myModifier == type.myModifier;
  }

  @Override
  public int hashCode() {
    return Objects.hash(myTypeParameters, myParametersType, myReturnType, myCallable, myModifier);
  }
}
