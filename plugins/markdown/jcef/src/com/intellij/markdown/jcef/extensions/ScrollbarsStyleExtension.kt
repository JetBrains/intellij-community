// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.jcef.extensions

import com.intellij.ui.jcef.JBCefScrollbarsHelper
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.ResourceProvider

internal class ScrollbarsStyleExtension : MarkdownBrowserPreviewExtension, ResourceProvider {
  override val styles: List<String> = listOf(STYLESHEET_FILENAME)

  override val resourceProvider: ResourceProvider = this

  override fun canProvide(resourceName: String): Boolean = resourceName in styles

  override fun loadResource(resourceName: String): ResourceProvider.Resource? {
    if (resourceName != STYLESHEET_FILENAME) {
      return null
    }
    return ResourceProvider.Resource(JBCefScrollbarsHelper.buildScrollbarsStyle().toByteArray())
  }

  override fun dispose() = Unit

  class Provider : MarkdownBrowserPreviewExtension.Provider {
    override fun createBrowserExtension(panel: MarkdownHtmlPanel): MarkdownBrowserPreviewExtension {
      return ScrollbarsStyleExtension()
    }
  }

  companion object {
    private const val STYLESHEET_FILENAME = "scrollbars.css"
  }
}
