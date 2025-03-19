// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Type of a particular function that is represented as a {@link PyCallable} in the PSI tree like lambda or function.
 * Use {@link #getCallable()} to get it.
 *
 */
public interface PyFunctionType extends PyCallableType {
  /**
   * @return actual callable element line function or lambda
   */
  @Override
  @NotNull
  PyCallable getCallable();

  @NotNull
  PyFunctionType dropSelf(@NotNull TypeEvalContext context);

  @Override
  default int getImplicitOffset() {
    return getCallable().asMethod() != null
           ? (!PyNames.NEW.equals(getName()) && getModifier() == PyAstFunction.Modifier.STATICMETHOD ? 0 : 1)
           : 0;
  }

  @Override
  default @Nullable PyFunction.Modifier getModifier() {
    final PyCallable callable = getCallable();
    return callable instanceof PyFunction ? ((PyFunction)callable).getModifier() : null;
  }
}
