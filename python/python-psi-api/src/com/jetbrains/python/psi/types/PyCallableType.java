// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A type instances of which can possibly be called. For example, a class definition can be called, and the result of a call is a class
 * instance.
 */
public interface PyCallableType extends PyType {
  /**
   * Returns true if the type is callable.
   */
  default boolean isCallable() {
    return true;
  }

  /**
   * Returns the return type of a function independent of a call site.
   *
   * For example, it may return a generic type.
   */
  @Nullable
  PyType getReturnType(@NotNull TypeEvalContext context);

  /**
   * Returns the type which is the result of calling an instance of this type.
   */
  @Nullable
  PyType getCallType(@NotNull TypeEvalContext context, @NotNull PyCallSiteExpression callSite);

  /**
   * Returns the list of parameter types.
   *
   * @return list of parameter info null if not applicable.
   */
  default @Nullable List<PyCallableParameter> getParameters(@NotNull TypeEvalContext context) {
    return null;
  }

  /**
   * Returns the parameters type as a variadic type.
   * This method provides a unified way to handle different forms of callable parameters:
   * <ul>
   * <li>{@link PyCallableParameterListType} for regular parameter lists</li>
   * <li>{@link PyParamSpecType} for a single ParamSpec parameter type <code>Callable[P, R]</code></li>
   * <li>{@link PyConcatenateType} for a single Concatenate type <code>Callable[Concatenate[T1, T2, P], R]</code></li>
   * </ul>
   * @return the parameters type, or null if not applicable
   */
  @ApiStatus.Experimental
  default @Nullable PyCallableParameterVariadicType getParametersType(@NotNull TypeEvalContext context) {
    return null;
  }

  @Override
  @Nullable
  default String getName() {
    final TypeEvalContext context = TypeEvalContext.codeInsightFallback(null);
    List<PyCallableParameter> parameters = getParameters(context);
    PyType returnType = getReturnType(context);
    return String.format("(%s) -> %s",
                         parameters != null ?
                         StringUtil.join(parameters,
                                         param -> {
                                           if (param != null) {
                                             final StringBuilder builder = new StringBuilder();
                                             final String name = param.getName();
                                             final PyType type = param.getType(context);
                                             if (name != null) {
                                               builder.append(name);
                                               builder.append(": ");
                                             }
                                             builder.append(type != null ? type.getName() : PyNames.ANY_TYPE);
                                             return builder.toString();
                                           }
                                           return PyNames.ANY_TYPE;
                                         },
                                         ", ") :
                         "...",
                         returnType != null ? returnType.getName() : PyNames.ANY_TYPE);
  }

  default @Nullable PyCallable getCallable() {
    return null;
  }

  @ApiStatus.Experimental
  @NotNull
  default PyCallableType dropSelf(@NotNull TypeEvalContext context) {
    return this;
  }

  default @Nullable PyFunction.Modifier getModifier() {
    return null;
  }

  /**
   * @return number of implicitly passed positional parameters; 0 means no parameters are passed implicitly.
   * Note that a <tt>*args</tt> is never marked as passed implicitly.
   * E.g. for a function like <tt>foo(a, b, *args)</tt> always holds <tt>getImplicitOffset() < 2</tt>.
   */
  default int getImplicitOffset() {
    return 0;
  }

  @Override
  default <T> T acceptTypeVisitor(@NotNull PyTypeVisitor<T> visitor) {
    return visitor.visitPyCallableType(this);
  }
}
