package org.intellij.plugins.markdown.extensions

import com.intellij.openapi.extensions.ExtensionPointName

interface MarkdownCodeFencePluginGeneratingProvider {
  /**
   * Check if plugin applicable for code fence language string
   */
  fun isApplicable(language: String): Boolean

  /**
   * Consumes code fence content and generates appropriate html
   * [language] is a language that is stated in a code fence
   * [raw] is raw representation of text in code fence
   */
  fun generateHtml(language: String, raw: String): String

  /**
   * Will be called on Look and Feel change, but before rendering of new preview
   * You should invalidate here all the caches that may be affected by old Look and Feel
   */
  fun onLAFChanged()

  companion object {
    val EP_NAME: ExtensionPointName<MarkdownCodeFencePluginGeneratingProvider> = ExtensionPointName.create(
      "org.intellij.markdown.codeFencePluginGeneratingProvider"
    )

    val all: Set<MarkdownCodeFencePluginGeneratingProvider>
      get() = EP_NAME.extensions.toSet()

    /**
     * Notify all [MarkdownCodeFencePluginGeneratingProvider] that Look and Feel has been changed
     */
    fun notifyLAFChanged() {
      all.forEach { it.onLAFChanged() }
    }
  }
}