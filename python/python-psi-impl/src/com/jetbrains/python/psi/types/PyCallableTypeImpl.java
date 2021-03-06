// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author vlan
 */
public class PyCallableTypeImpl implements PyCallableType {
  @Nullable private final List<PyCallableParameter> myParameters;
  @Nullable private final PyType myReturnType;
  @Nullable private final PyCallable myCallable;
  @Nullable private final PyFunction.Modifier myModifier;
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

  @Nullable
  @Override
  public PyType getReturnType(@NotNull TypeEvalContext context) {
    return myReturnType;
  }

  @Nullable
  @Override
  public PyType getCallType(@NotNull TypeEvalContext context, @NotNull PyCallSiteExpression callSite) {
    if (!PyTypeChecker.hasGenerics(myReturnType, context)) {
      return myReturnType;
    }

    PyCallExpression.PyArgumentsMapping fullMapping = PyCallExpressionHelper.mapArguments(callSite, this, context);
    Map<PyExpression, PyCallableParameter> actualParameters = fullMapping.getMappedParameters();
    List<PyCallableParameter> allParameters = ContainerUtil.notNullize(getParameters(context));
    return analyzeCallType(myReturnType, actualParameters, allParameters, context);
  }

  @Nullable
  private static PyType analyzeCallType(@Nullable PyType type,
                                        @NotNull Map<PyExpression, PyCallableParameter> actualParameters,
                                        @NotNull Collection<PyCallableParameter> allParameters,
                                        @NotNull TypeEvalContext context) {
    Map<PyGenericType, PyType> substitutions = PyTypeChecker.unifyGenericCall(null, actualParameters, context);
    Map<PyGenericType, PyType> substitutionsWithUnresolvedReturnGenerics =
      PyTypeChecker.getSubstitutionsWithUnresolvedReturnGenerics(allParameters, type, substitutions, context);
    return PyTypeChecker.substitute(type, substitutionsWithUnresolvedReturnGenerics, context);
  }

  @Nullable
  @Override
  public List<PyCallableParameter> getParameters(@NotNull TypeEvalContext context) {
    return myParameters;
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
    return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
  }

  @Nullable
  @Override
  public String getName() {
    final TypeEvalContext context = TypeEvalContext.codeInsightFallback(null);
    return String.format("(%s) -> %s",
                         myParameters != null ?
                         StringUtil.join(myParameters,
                                         param -> {
                                           if (param != null) {
                                             final StringBuilder builder = new StringBuilder();
                                             final String name = param.getName();
                                             final PyType type = param.getType(context);
                                             if (name != null) {
                                               builder.append(name);
                                               if (type != null) {
                                                 builder.append(": ");
                                               }
                                             }
                                             builder.append(type != null ? type.getName() : PyNames.UNKNOWN_TYPE);
                                             return builder.toString();
                                           }
                                           return PyNames.UNKNOWN_TYPE;
                                         },
                                         ", ") :
                         "...",
                         myReturnType != null ? myReturnType.getName() : PyNames.UNKNOWN_TYPE);
  }

  @Override
  public boolean isBuiltin() {
    return false;
  }

  @Override
  public void assertValid(String message) {
  }

  @Override
  @Nullable
  public PyCallable getCallable() {
    return myCallable;
  }

  @Override
  @Nullable
  public PyFunction.Modifier getModifier() {
    return myModifier;
  }

  @Override
  public int getImplicitOffset() {
    return myImplicitOffset;
  }
}
