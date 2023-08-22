// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions

import com.intellij.openapi.extensions.ExtensionPointName
import org.intellij.markdown.ast.ASTNode
import org.jetbrains.annotations.ApiStatus

/**
 * This extension point allows generating custom HTML for code fences.
 *
 * Implementors of this interface will be automatically added
 * to the list of extensions on the settings page.
 */
@ApiStatus.Obsolete
@ApiStatus.Internal
interface CodeFenceGeneratingProvider {
  /**
   * Check if plugin applicable for code fence language string
   */
  fun isApplicable(language: String): Boolean

  /**
   * Consumes code fence content and generates appropriate html
   * [language] is a language that is stated in a code fence
   * [raw] is raw representation of text in code fence
   * [node] is code fence AST node
   */
  fun generateHtml(language: String, raw: String, node: ASTNode): String

  companion object {
    private val EP_NAME = ExtensionPointName<CodeFenceGeneratingProvider>("org.intellij.markdown.fenceGeneratingProvider")

    @ApiStatus.Internal
    @JvmStatic
    fun collectProviders(): Collection<CodeFenceGeneratingProvider> {
      return EP_NAME.extensionList
    }
  }
}
