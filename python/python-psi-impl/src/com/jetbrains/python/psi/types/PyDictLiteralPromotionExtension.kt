// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.intellij.openapi.extensions.ExtensionPointName
import com.jetbrains.python.psi.PyDictLiteralExpression
import com.jetbrains.python.psi.PyExpression
import org.jetbrains.annotations.ApiStatus

/**
 * Lets a framework accept a dict literal as a value of a class type.
 * A framework uses it when it converts a dict to an instance of the class at runtime.
 */
@ApiStatus.Internal
interface PyDictLiteralPromotionExtension {
  /**
   * Returns `true` if [dictLiteral] is a valid value of the instance type [expectedType].
   * [promoteValue] gives the type of a nested value expression for its expected type.
   */
  fun acceptsDictLiteral(
    expectedType: PyClassType,
    dictLiteral: PyDictLiteralExpression,
    promoteValue: (expectedType: PyType?, value: PyExpression) -> PyType?,
    context: TypeEvalContext,
  ): Boolean

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<PyDictLiteralPromotionExtension> = ExtensionPointName.create("Pythonid.dictLiteralPromotionExtension")
  }
}
