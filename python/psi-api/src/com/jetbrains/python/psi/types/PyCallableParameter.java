// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyParameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

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

  /**
   * @apiNote This method will be marked as abstract in 2018.2.
   */
  @Nullable
  default String getDefaultValueText() {
    return null;
  }

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
   * @param includeDefaultValue if true, include the default value after an "=".
   * @param context             context to be used to resolve argument type
   * @param typeFilter          predicate to be used to ignore resolved argument type
   * @return canonical representation of parameter.
   * Includes asterisks for *param and **param.
   * Also includes argument type if {@code context} is not null and filter returns `false` for it.
   * @apiNote This method will be marked as abstract in 2018.3.
   */
  @NotNull
  default String getPresentableText(boolean includeDefaultValue, @Nullable TypeEvalContext context, @NotNull Predicate<PyType> typeFilter) {
    return getPresentableText(includeDefaultValue, context);
  }

  /**
   * @param context context to be used to resolve argument type
   * @return argument type. Returns element type for *param and value type for **param.
   */
  @Nullable
  PyType getArgumentType(@NotNull TypeEvalContext context);
}
