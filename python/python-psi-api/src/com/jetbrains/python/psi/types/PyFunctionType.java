/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi.types;

import com.jetbrains.python.PyNames;
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
           ? (!PyNames.NEW.equals(getName()) && getModifier() == PyFunction.Modifier.STATICMETHOD ? 0 : 1)
           : 0;
  }

  @Override
  @Nullable
  default PyFunction.Modifier getModifier() {
    final PyCallable callable = getCallable();
    return callable instanceof PyFunction ? ((PyFunction)callable).getModifier() : null;
  }
}
