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

import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyParameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author vlan
 */
public interface PyCallableParameter {

  /**
   * @return name of the parameter.
   * Returns null if the parameter is tuple or star, or name is unknown.
   */
  @Nullable
  String getName();

  /**
   * @param context type evaluation context
   * @return type of the parameter.
   */
  @Nullable
  PyType getType(@NotNull TypeEvalContext context);

  /**
   * @return underneath psi element if exists.
   */
  @Nullable
  PyParameter getParameter();

  @Nullable
  PyExpression getDefaultValue();

  boolean hasDefaultValue();

  @Nullable
  String getDefaultValueText();

  boolean isPositionalContainer();

  boolean isKeywordContainer();

  boolean isSelf();

  /**
   * @param includeDefaultValue if true, include the default value after an "=".
   * @return canonical representation of parameter.
   * Includes asterisks for *param and **param.
   */
  @NotNull
  default String getPresentableText(boolean includeDefaultValue) {
    return getPresentableText(includeDefaultValue, null);
  }

  /**
   * @param includeDefaultValue if true, include the default value after an "=".
   * @param context             context to be used to resolve argument type
   * @return canonical representation of parameter.
   * Includes asterisks for *param and **param.
   * Also includes argument type if {@code context} is not null and resolved type is not unknown.
   */
  @NotNull
  String getPresentableText(boolean includeDefaultValue, @Nullable TypeEvalContext context);

  /**
   * @param context context to be used to resolve argument type
   * @return argument type. Returns element type for *param and value type for **param.
   */
  @Nullable
  PyType getArgumentType(@NotNull TypeEvalContext context);
}
