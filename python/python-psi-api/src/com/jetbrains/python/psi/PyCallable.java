// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstCallable;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Something that can be called, passed parameters to, and return something back.
 */
public interface PyCallable extends PyAstCallable, PyTypedElement, PyQualifiedNameOwner {

  /**
   * @return a list of parameters passed to this callable, possibly empty.
   */
  @Override
  @NotNull
  PyParameterList getParameterList();

  /**
   * Same as {@link PyCallable#getParameterList()} but more flexible because
   * parameters could be provided and have no representation in the code.
   *
   * @param context type evaluation context
   * @return a list of parameters passed to this callable, possibly empty.
   */
  @NotNull
  List<PyCallableParameter> getParameters(@NotNull TypeEvalContext context);

  /**
   * Returns the return type of the callable independent of a call site.
   */
  @Nullable
  PyType getReturnType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key);

  /**
   * Returns the type of the call to the callable.
   */
  @Nullable
  PyType getCallType(@NotNull TypeEvalContext context, @NotNull PyCallSiteExpression callSite);

  /**
   * Please use getCallType with four arguments instead
   */
  @Deprecated(forRemoval = true)
  default @Nullable PyType getCallType(@Nullable PyExpression receiver,
                             @NotNull Map<PyExpression, PyCallableParameter> parameters,
                             @NotNull TypeEvalContext context) {
    return getCallType(receiver, null, parameters, context);
  }



  /**
   * Returns the type of the call to the callable where the call site is specified by the optional receiver and the arguments to parameters
   * mapping.
   */
  @Nullable
  PyType getCallType(@Nullable PyExpression receiver,
                     @Nullable PyCallSiteExpression pyCallSiteExpression,
                     @NotNull Map<PyExpression, PyCallableParameter> parameters,
                     @NotNull TypeEvalContext context);

  /**
   * @return a methods returns itself, non-method callables return null.
   */
  @Override
  @Nullable
  PyFunction asMethod();
}
