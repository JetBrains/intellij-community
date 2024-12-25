// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyFunction;
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

  default @Nullable PyCallable getCallable() {
    return null;
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
}
