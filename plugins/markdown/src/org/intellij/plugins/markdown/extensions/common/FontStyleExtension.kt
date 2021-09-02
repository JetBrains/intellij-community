// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.common

import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.extensions.MarkdownExtension
import org.intellij.plugins.markdown.extensions.jcef.MarkdownJCEFPreviewExtension
import org.intellij.plugins.markdown.ui.preview.ResourceProvider

internal class FontStyleExtension : MarkdownJCEFPreviewExtension, ResourceProvider {
  private val settings
    get() = MarkdownExtension.currentProjectSettings

  override val priority: MarkdownBrowserPreviewExtension.Priority
    get() = MarkdownBrowserPreviewExtension.Priority.AFTER_ALL

  override val styles: List<String> = listOf("font/font-settings.css")

  override val resourceProvider: ResourceProvider = this

  override fun canProvide(resourceName: String): Boolean = resourceName in styles

  override fun loadResource(resourceName: String): ResourceProvider.Resource {
    val css = getFontSizeCss(settings.fontSize, checkNotNull(settings.fontFamily))
    return ResourceProvider.Resource(css.toByteArray())
  }

  companion object {
    private fun getFontSizeCss(fontSize: Int, fontFamily: String): String {
      // language=CSS
      return """
        div { 
          font-size: ${fontSize}px !important; 
          font-family: ${fontFamily} !important; 
        }
      """.trimIndent()
    }
  }
}
