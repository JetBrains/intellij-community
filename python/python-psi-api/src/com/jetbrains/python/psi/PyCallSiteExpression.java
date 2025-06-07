// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstCallSiteExpression;
import com.jetbrains.python.ast.PyAstCallable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Marker interface for Python expressions that are call sites for explicit or implicit function calls.
 *
 */
public interface PyCallSiteExpression extends PyAstCallSiteExpression, PyExpression {

  /**
   * Returns an expression that is treated as a receiver for this explicit or implicit (read, operator) call.
   * <p>
   * For most operator expressions it returns the result of {@code getOperator()} since it naturally represents
   * the object on which a special magic method is called. However for binary expressions that additionally
   * can be reversible such as {@code __add__} and {@code __radd__} it also takes into account name of the
   * actual callee method and chained comparisons order if any.
   *
   * @param resolvedCallee optional callee corresponding to the call. Without it the receiver is deduced purely syntactically.
   */
  default @Nullable PyExpression getReceiver(@Nullable PyCallable resolvedCallee) {
    return (PyExpression)getReceiver((PyAstCallable)resolvedCallee);
  }

  default @NotNull List<PyExpression> getArguments(@Nullable PyCallable resolvedCallee) {
    //noinspection unchecked
    return (List<PyExpression>)getArguments((PyAstCallable)resolvedCallee);
  }
}
