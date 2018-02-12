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
package com.jetbrains.python.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Marker interface for Python expressions that are call sites for explicit or implicit function calls.
 *
 * @author vlan
 */
public interface PyCallSiteExpression extends PyExpression {

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
  @Nullable
  PyExpression getReceiver(@Nullable PyCallable resolvedCallee);

  @NotNull
  List<PyExpression> getArguments(@Nullable PyCallable resolvedCallee);
}
