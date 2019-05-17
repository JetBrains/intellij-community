package org.intellij.plugins.markdown.extensions

import com.intellij.openapi.extensions.ExtensionPointName

interface MarkdownCodeFencePluginGeneratingProvider {
  /**
   * Check if plugin applicable for code fence language string
   */
  fun isApplicable(language: String): Boolean

  /**
   * Consumes code fence content
   */
  fun generateHtml(text: String): String

  companion object {
    val EP_NAME: ExtensionPointName<MarkdownCodeFencePluginGeneratingProvider> = ExtensionPointName.create<MarkdownCodeFencePluginGeneratingProvider>("org.intellij.markdown.codeFencePluginGeneratingProvider")
  }
}