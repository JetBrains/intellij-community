// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.intellij.psi.PsiElement
import com.jetbrains.python.ProtectionLevel
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyParameter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

interface PyCallableParameter {
  val name: @Nls String?

  /**
   * @param context type evaluation context
   * @return type of the parameter.
   */
  fun getType(context: TypeEvalContext): PyType?

  /**
   * @return underneath psi element if exists.
   */
  val parameter: PyParameter?

  val declarationElement: PsiElement?
    get() = parameter

  val defaultValue: PyExpression?

  fun hasDefaultValue(): Boolean

  val defaultValueText: String?

  val isPositionalContainer: Boolean

  val isKeywordContainer: Boolean

  val isSelf: Boolean

  @get:ApiStatus.Experimental
  val isPositionOnlySeparator: Boolean

  @get:ApiStatus.Experimental
  val isKeywordOnlySeparator: Boolean

  @get:ApiStatus.Experimental
  val protectionLevel: ProtectionLevel
    get() = ProtectionLevel.forName(name.orEmpty())

  /**
   * @param includeDefaultValue if true, include the default value after an "=".
   * @return canonical representation of parameter.
   * Includes asterisks for *param and **param.
   */
  fun getPresentableText(includeDefaultValue: Boolean): String =
    getPresentableText(includeDefaultValue, null)

  /**
   * @param includeDefaultValue if true, include the default value after an "=".
   * @param context             context to be used to resolve argument type
   * @return canonical representation of parameter.
   * Includes asterisks for *param and **param.
   * Also includes argument type if `context` is not null and resolved type is not unknown.
   */
  fun getPresentableText(includeDefaultValue: Boolean, context: TypeEvalContext?): String

  /**
   * @param includeDefaultValue if true, include the default value after an "=".
   * @param context             context to be used to resolve argument type
   * @param typeFilter          predicate to be used to ignore resolved argument type
   * @return canonical representation of parameter.
   * Includes asterisks for *param and **param.
   * Also includes argument type if `context` is not null and filter returns `false` for it.
   */
  fun getPresentableText(includeDefaultValue: Boolean, context: TypeEvalContext?, typeFilter: (PyType?) -> Boolean): String

  /**
   * @param context context to be used to resolve argument type
   * @return argument type. Returns element type for *param and value type for **param.
   */
  fun getArgumentType(context: TypeEvalContext): PyType?
}
