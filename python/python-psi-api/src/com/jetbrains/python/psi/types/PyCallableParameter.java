// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyParameter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public interface PyCallableParameter {

  /**
   * @return name of the parameter.
   * Returns null if the parameter is tuple or star, or name is unknown.
   */
  @Nullable
  @Nls String getName();

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

  default @Nullable PsiElement getDeclarationElement() {
    return getParameter();
  }

  @Nullable
  PyExpression getDefaultValue();

  boolean hasDefaultValue();

  @Nullable
  String getDefaultValueText();

  boolean isPositionalContainer();

  boolean isKeywordContainer();

  boolean isSelf();

  @ApiStatus.Experimental
  boolean isPositionOnlySeparator();

  @ApiStatus.Experimental
  boolean isKeywordOnlySeparator();

  /**
   * @param includeDefaultValue if true, include the default value after an "=".
   * @return canonical representation of parameter.
   * Includes asterisks for *param and **param.
   */
  default @NotNull String getPresentableText(boolean includeDefaultValue) {
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
   */
  @NotNull
  String getPresentableText(boolean includeDefaultValue, @Nullable TypeEvalContext context, @NotNull Predicate<PyType> typeFilter);

  /**
   * @param context context to be used to resolve argument type
   * @return argument type. Returns element type for *param and value type for **param.
   */
  @Nullable
  PyType getArgumentType(@NotNull TypeEvalContext context);
}
