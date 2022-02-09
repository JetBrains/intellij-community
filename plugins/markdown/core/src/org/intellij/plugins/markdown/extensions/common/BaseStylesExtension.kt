// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.common

import com.intellij.openapi.project.Project
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.PreviewLAFThemeStyles
import org.intellij.plugins.markdown.ui.preview.ResourceProvider
import java.io.File

internal class BaseStylesExtension(private val project: Project?) : MarkdownBrowserPreviewExtension, ResourceProvider {
  override val priority = MarkdownBrowserPreviewExtension.Priority.BEFORE_ALL

  override val styles: List<String> = listOf("baseStyles/default.css", COLORS_CSS_FILENAME)

  override val resourceProvider: ResourceProvider = this

  override fun loadResource(resourceName: String): ResourceProvider.Resource? {
    if (resourceName == COLORS_CSS_FILENAME) {
      return ResourceProvider.Resource(PreviewLAFThemeStyles.createStylesheet().toByteArray())
    }
    val settings = project?.let(MarkdownSettings::getInstance)
    val path = settings?.customStylesheetPath.takeIf { settings?.useCustomStylesheetPath == true }
    return when (path) {
      null -> ResourceProvider.loadInternalResource(BaseStylesExtension::class, resourceName)
      else -> ResourceProvider.loadExternalResource(File(path))
    }
  }

  override fun canProvide(resourceName: String): Boolean = resourceName in styles

  override fun dispose() = Unit

  class Provider: MarkdownBrowserPreviewExtension.Provider {
    override fun createBrowserExtension(panel: MarkdownHtmlPanel): MarkdownBrowserPreviewExtension {
      return BaseStylesExtension(panel.project)
    }
  }

  companion object {
    private const val COLORS_CSS_FILENAME = "baseStyles/colors.css"
  }
}
