// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types.external

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * An external type provider invoked by [com.jetbrains.python.psi.types.TypeEvalContext] to obtain a type from a separate engine.
 * Implementations may return `null` if they cannot provide a type for the given element.
 */
@ApiStatus.Internal
interface ExternalPyTypeResolverProvider {
  fun createResolver(project: Project): ExternalPyTypeResolver? = null

  companion object {
    private val EP_NAME: ExtensionPointName<ExternalPyTypeResolverProvider> =
      ExtensionPointName.Companion.create("Pythonid.typeEvalExternalTypeProvider")

    @JvmStatic
    @ApiStatus.Internal
    fun createTypeResolver(project: Project): ExternalPyTypeResolver? {
      return EP_NAME.extensionList.firstNotNullOfOrNull { it.createResolver(project) }
    }
  }
}