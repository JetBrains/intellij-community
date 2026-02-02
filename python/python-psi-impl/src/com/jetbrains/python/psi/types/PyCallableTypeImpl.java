// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PyCallableTypeImpl implements PyCallableType {
  private final @Nullable List<PyCallableParameter> myParameters;
  private final @Nullable PyType myReturnType;
  private final @Nullable PyCallable myCallable;
  private final @Nullable PyFunction.Modifier myModifier;
  private final int myImplicitOffset;

  public PyCallableTypeImpl(@Nullable List<PyCallableParameter> parameters, @Nullable PyType returnType) {
    this(parameters, returnType, null, null, 0);
  }

  public PyCallableTypeImpl(@Nullable List<PyCallableParameter> parameters,
                            @Nullable PyType returnType,
                            @Nullable PyCallable callable,
                            @Nullable PyFunction.Modifier modifier,
                            int offset) {
    myParameters = parameters;
    myReturnType = returnType;
    myCallable = callable;
    myModifier = modifier;
    myImplicitOffset = offset;
  }

  @Override
  public @Nullable PyType getReturnType(@NotNull TypeEvalContext context) {
    return myReturnType;
  }

  @Override
  public @Nullable PyType getCallType(@NotNull TypeEvalContext context, @NotNull PyCallSiteExpression callSite) {
    if (!PyTypeChecker.hasGenerics(myReturnType, context)) {
      return PyNarrowedType.Companion.bindIfNeeded(myReturnType, callSite);
    }

    final var fullMapping = PyCallExpressionHelper.mapArguments(callSite, this, context);
    final var actualParameters = fullMapping.getMappedParameters();
    final var allParameters = ContainerUtil.notNullize(getParameters(context));
    final var receiver = callSite.getReceiver(this.myCallable);
    return analyzeCallType(myReturnType, actualParameters, allParameters, receiver, callSite, context);
  }

  private static @Nullable PyType analyzeCallType(@Nullable PyType type,
                                                  @NotNull Map<PyExpression, PyCallableParameter> actualParameters,
                                                  @NotNull Collection<PyCallableParameter> allParameters,
                                                  @Nullable PyExpression receiver,
                                                  @NotNull PyCallSiteExpression callsite,
                                                  @NotNull TypeEvalContext context) {
    final var substitutions = PyTypeChecker.unifyGenericCall(receiver, actualParameters, context);
    final var substitutionsWithUnresolvedReturnGenerics =
      PyTypeChecker.getSubstitutionsWithUnresolvedReturnGenerics(allParameters, type, substitutions, context);
    PyType typeAfterSubstitution = PyTypeChecker.substitute(type, substitutionsWithUnresolvedReturnGenerics, context);
    return PyNarrowedType.Companion.bindIfNeeded(typeAfterSubstitution, callsite);
  }

  @Override
  public @Nullable List<PyCallableParameter> getParameters(@NotNull TypeEvalContext context) {
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
  public int getImplicitOffset() {
    return myImplicitOffset;
  }

  @Override
  public @Nullable PyQualifiedNameOwner getDeclarationElement() {
    return myCallable;
  }

  @Override
  public @NotNull PyCallableType dropSelf(@NotNull TypeEvalContext context) {
    final List<PyCallableParameter> parameters = getParameters(context);
    if (parameters != null && myCallable instanceof PyFunction function) {
      final List<PyCallableParameter> functionParameters = function.getParameters(context);

      if (!ContainerUtil.isEmpty(parameters) && !ContainerUtil.isEmpty(functionParameters) && functionParameters.get(0).isSelf()) {
        List<PyCallableParameter> newParameters = ContainerUtil.subList(parameters, 1);
        return new PyCallableTypeImpl(newParameters, myReturnType, myCallable, myModifier, myImplicitOffset);
      }
    }

    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PyCallableTypeImpl type = (PyCallableTypeImpl)o;
    return myImplicitOffset == type.myImplicitOffset &&
           Objects.equals(myParameters, type.myParameters) &&
           Objects.equals(myReturnType, type.myReturnType) &&
           Objects.equals(myCallable, type.myCallable) &&
           myModifier == type.myModifier;
  }

  @Override
  public int hashCode() {
    return Objects.hash(myParameters, myReturnType, myCallable, myModifier, myImplicitOffset);
  }
}
