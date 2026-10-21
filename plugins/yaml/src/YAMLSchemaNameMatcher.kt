// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

/**
 * Tells if the JSON schema of the file defines a YAML key or scalar, for example as a property name or an enum value.
 * The spellchecker and Grazie do not check such an element.
 *
 * The JSON schema implementation is in `intellij.yaml.backend`.
 * Without the backend, no element matches.
 */
@ApiStatus.Internal
interface YAMLSchemaNameMatcher {
  /**
   * @param element a [YAMLTokenTypes.SCALAR_KEY] token, a scalar token, or a [org.jetbrains.yaml.psi.YAMLScalar]
   */
  fun matches(element: PsiElement): Boolean

  companion object {
    private val EP_NAME = ExtensionPointName<YAMLSchemaNameMatcher>("com.intellij.yaml.schemaNameMatcher")

    @JvmStatic
    fun matchesNameFromSchema(element: PsiElement): Boolean {
      return EP_NAME.extensionList.any { it.matches(element) }
    }
  }
}
