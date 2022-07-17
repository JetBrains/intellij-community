// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.common

import com.intellij.openapi.project.Project
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.ResourceProvider

internal class FontStyleExtension(private val project: Project): MarkdownBrowserPreviewExtension, ResourceProvider {
  override val priority: MarkdownBrowserPreviewExtension.Priority
    get() = MarkdownBrowserPreviewExtension.Priority.AFTER_ALL

  override val styles: List<String> = listOf("font/font-settings.css")

  override val resourceProvider: ResourceProvider = this

  override fun canProvide(resourceName: String): Boolean = resourceName in styles

  override fun loadResource(resourceName: String): ResourceProvider.Resource {
    val settings = MarkdownSettings.getInstance(project)
    val css = getFontSizeCss(settings.fontSize, checkNotNull(settings.fontFamily))
    return ResourceProvider.Resource(css.toByteArray())
  }

  override fun dispose() = Unit

  class Provider: MarkdownBrowserPreviewExtension.Provider {
    override fun createBrowserExtension(panel: MarkdownHtmlPanel): MarkdownBrowserPreviewExtension? {
      val project = panel.project ?: return null
      return FontStyleExtension(project)
    }
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
