// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.ast.PyAstFunction.Modifier.STATICMETHOD;
import static com.jetbrains.python.psi.PyUtil.as;

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
  private final @NotNull List<PyCallableParameter> myParameters;

  /**
   * @deprecated Use {@link PyFunctionTypeImpl#create(PyCallable, TypeEvalContext)}
   */
  @Deprecated
  public PyFunctionTypeImpl(@NotNull PyCallable callable) {
    this(callable, ContainerUtil.map(callable.getParameterList().getParameters(), PyCallableParameterImpl::psi));
  }

  public PyFunctionTypeImpl(@NotNull PyCallable callable, @NotNull List<PyCallableParameter> parameters) {
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
  public @Nullable List<PyCallableParameter> getParameters(@NotNull TypeEvalContext context) {
    return myParameters;
  }

  @Override
  public List<? extends RatedResolveResult> resolveMember(@NotNull String name,
                                                          @Nullable PyExpression location,
                                                          @NotNull AccessDirection direction,
                                                          @NotNull PyResolveContext resolveContext) {
    final PyClassType delegate = selectCallableType(location, resolveContext.getTypeEvalContext());
    if (delegate == null) {
      return Collections.emptyList();
    }
    return delegate.resolveMember(name, location, direction, resolveContext);
  }

  @Override
  public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
    final TypeEvalContext typeEvalContext = TypeEvalContext.codeCompletion(location.getProject(), location.getContainingFile());
    final PyClassType delegate;
    if (location instanceof PyReferenceExpression) {
      delegate = selectCallableType(((PyReferenceExpression)location).getQualifier(), typeEvalContext);
    }
    else {
      final PyClass cls = PyPsiFacade.getInstance(myCallable.getProject()).createClassByQName(PyNames.TYPES_FUNCTION_TYPE, myCallable);
      delegate = cls != null ? new PyClassTypeImpl(cls, false) : null;
    }
    if (delegate == null) {
      return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }
    return delegate.getCompletionVariants(completionPrefix, location, context);
  }

  private @Nullable PyClassType selectCallableType(@Nullable PyExpression location, @NotNull TypeEvalContext context) {
    final String className;
    if (location instanceof PyReferenceExpression && isBoundMethodReference((PyReferenceExpression)location, context)) {
      className = PyNames.TYPES_METHOD_TYPE;
    }
    else {
      className = PyNames.TYPES_FUNCTION_TYPE;
    }
    final PyClass cls = PyPsiFacade.getInstance(myCallable.getProject()).createClassByQName(className, myCallable);
    return cls != null ? new PyClassTypeImpl(cls, false) : null;
  }

  private boolean isBoundMethodReference(@NotNull PyReferenceExpression location, @NotNull TypeEvalContext context) {
    final PyFunction function = as(getCallable(), PyFunction.class);
    final boolean isNonStaticMethod = function != null && function.getContainingClass() != null && function.getModifier() != STATICMETHOD;
    if (isNonStaticMethod) {
      // In Python 2 unbound methods have __method fake type
      if (LanguageLevel.forElement(location).isPython2()) {
        return true;
      }
      final PyExpression qualifier;
      if (location.isQualified()) {
        qualifier = location.getQualifier();
      }
      else {
        final PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
        qualifier = ContainerUtil.getLastItem(location.followAssignmentsChain(resolveContext).getQualifiers());
      }
      if (qualifier != null) {
        final PyType qualifierType = context.getType(qualifier);
        if (PyTypeUtil.toStream(qualifierType).select(PyClassType.class).anyMatch(it -> !it.isDefinition())) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public String getName() {
    return "function";
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
}
