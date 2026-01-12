// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.PyTypedElement

/**
 * An external type provider invoked by [TypeEvalContext] to obtain a type from a separate engine.
 * Implementations may return `null` if they cannot provide a type for the given element.
 */
interface TypeEvalExternalTypeProvider {
  fun isAvailable(): Boolean

  fun provideType(element: PyTypedElement, context: TypeEvalContext): Ref<PyType?>?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<TypeEvalExternalTypeProvider> =
      ExtensionPointName.create("Pythonid.typeEvalExternalTypeProvider")
  }
}
