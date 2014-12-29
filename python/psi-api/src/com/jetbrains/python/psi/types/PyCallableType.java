/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
  boolean isCallable();

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
  List<PyCallableParameter> getParameters(@NotNull TypeEvalContext context);
}
