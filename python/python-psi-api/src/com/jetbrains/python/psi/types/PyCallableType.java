/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A type instances of which can possibly be called. For example, a class definition can be called, and the result of a call is a class
 * instance.
 *
 * @author yole
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
   * @param context
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
  @Nullable
  default List<PyCallableParameter> getParameters(@NotNull TypeEvalContext context) {
    return null;
  }

  @Nullable
  default PyCallable getCallable() {
    return null;
  }

  @Nullable
  default PyFunction.Modifier getModifier() {
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
