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
package com.jetbrains.python.psi;

import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Something that can be called, passed parameters to, and return something back.

 * @author dcheryasov
 */
public interface PyCallable extends PyTypedElement, PyQualifiedNameOwner {

  /**
   * @return a list of parameters passed to this callable, possibly empty.
   */
  @NotNull
  PyParameterList getParameterList();

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
   * Returns the type of the call to the callable where the call site is specified by the optional receiver and the arguments to parameters
   * mapping.
   */
  @Nullable
  PyType getCallType(@Nullable PyExpression receiver, @NotNull Map<PyExpression, PyNamedParameter> parameters,
                     @NotNull TypeEvalContext context);

  /**
   * @return a methods returns itself, non-method callables return null.
   */
  @Nullable
  PyFunction asMethod();
}
