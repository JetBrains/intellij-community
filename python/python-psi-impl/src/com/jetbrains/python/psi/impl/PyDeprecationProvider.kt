// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.Nls

/**
 * Reports deprecations that a library declares in its own terms rather than with PEP 702 `@deprecated`, for example a
 * model field marked deprecated by a field specifier argument. Consulted for resolved symbols that carry no deprecation
 * message of their own.
 */
interface PyDeprecationProvider {
  /** The deprecation message for [element], or `null` if this provider does not consider it deprecated. */
  fun getDeprecationMessage(element: PsiElement, context: TypeEvalContext): @Nls String?

  companion object {
    val EP_NAME: ExtensionPointName<PyDeprecationProvider> = ExtensionPointName.create("Pythonid.pyDeprecationProvider")

    /** The first deprecation message any registered provider reports for [element]. */
    @JvmStatic
    fun findDeprecationMessage(element: PsiElement, context: TypeEvalContext): @Nls String? =
      EP_NAME.computeSafeIfAny { it.getDeprecationMessage(element, context) }
  }
}
