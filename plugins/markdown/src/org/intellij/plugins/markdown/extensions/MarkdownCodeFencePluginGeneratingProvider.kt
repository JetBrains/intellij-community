// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions

import org.intellij.markdown.ast.ASTNode

/**
 * Implementors of this interface will be automatically added
 * to the list of extensions on the settings page.
 */
interface MarkdownCodeFencePluginGeneratingProvider : MarkdownExtension {
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
  fun onLAFChanged()

  companion object {
    @JvmStatic
    val all: List<MarkdownCodeFencePluginGeneratingProvider>
      get() = MarkdownExtension.all.filterIsInstance<MarkdownCodeFencePluginGeneratingProvider>()

    /**
     * Notify all [MarkdownCodeFencePluginGeneratingProvider] that Look and Feel has been changed
     */
    fun notifyLAFChanged() {
      all.forEach { it.onLAFChanged() }
    }
  }
}
