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
@ApiStatus.Experimental
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

  /**
   * Will be called on Look and Feel change, but before rendering of new preview
   * You should invalidate here all the caches that may be affected by old Look and Feel
   */
  fun onLaFChanged() {
  }

  companion object {
    @PublishedApi
    internal val EP_NAME = ExtensionPointName<CodeFenceGeneratingProvider>("org.intellij.markdown.fenceGeneratingProvider")

    internal val all: Set<CodeFenceGeneratingProvider>
      get() = EP_NAME.extensionList.toSet()

    /**
     * Notify all [CodeFenceGeneratingProvider] that Look and Feel has been changed
     */
    fun notifyLaFChanged() {
      EP_NAME.extensionList.forEach(CodeFenceGeneratingProvider::onLaFChanged)
    }
  }
}
