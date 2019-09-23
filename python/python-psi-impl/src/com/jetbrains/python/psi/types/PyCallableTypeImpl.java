// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author vlan
 */
public class PyCallableTypeImpl implements PyCallableType {
  @Nullable private final List<PyCallableParameter> myParameters;
  @Nullable private final PyType myReturnType;

  public PyCallableTypeImpl(@Nullable List<PyCallableParameter> parameters, @Nullable PyType returnType) {
    myParameters = parameters;
    myReturnType = returnType;
  }

  @Nullable
  @Override
  public PyType getReturnType(@NotNull TypeEvalContext context) {
    return myReturnType;
  }

  @Nullable
  @Override
  public PyType getCallType(@NotNull TypeEvalContext context, @NotNull PyCallSiteExpression callSite) {
    return myReturnType;
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
}
