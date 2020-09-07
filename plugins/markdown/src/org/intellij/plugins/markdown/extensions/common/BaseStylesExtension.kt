// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.common

import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.extensions.jcef.MarkdownJCEFPreviewExtension
import org.intellij.plugins.markdown.settings.MarkdownApplicationSettings
import org.intellij.plugins.markdown.ui.preview.PreviewLAFThemeStyles
import org.intellij.plugins.markdown.ui.preview.ResourceProvider
import java.io.File

internal class BaseStylesExtension : MarkdownJCEFPreviewExtension, ResourceProvider {
  override val priority = MarkdownBrowserPreviewExtension.Priority.BEFORE_ALL

  override val styles: List<String> = listOf("baseStyles/default.css", COLORS_CSS_FILENAME)

  override val resourceProvider: ResourceProvider = this

  override fun loadResource(resourceName: String): ResourceProvider.Resource? {
    if (resourceName == COLORS_CSS_FILENAME) {
      return ResourceProvider.Resource(PreviewLAFThemeStyles.createStylesheet().toByteArray())
    }
    with(MarkdownApplicationSettings.getInstance().markdownCssSettings) {
      return if (isCustomStylesheetEnabled) {
        ResourceProvider.loadExternalResource(File(customStylesheetPath))
      }
      else {
        ResourceProvider.loadInternalResource(BaseStylesExtension::class, resourceName)
      }
    }
  }

  override fun canProvide(resourceName: String): Boolean = resourceName in styles

  companion object {
    private const val COLORS_CSS_FILENAME = "baseStyles/colors.css"
  }
}
