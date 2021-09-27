// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.common

import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.extensions.MarkdownExtension
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.ResourceProvider

internal class InlineStylesExtension : MarkdownBrowserPreviewExtension, ResourceProvider {
  override val priority: MarkdownBrowserPreviewExtension.Priority
    get() = MarkdownBrowserPreviewExtension.Priority.AFTER_ALL

  override val styles: List<String> = listOf("inlineStyles/inline.css")

  override val resourceProvider: ResourceProvider = this

  override fun canProvide(resourceName: String): Boolean = resourceName in styles

  override fun loadResource(resourceName: String): ResourceProvider.Resource {
    return when (val text = MarkdownExtension.currentProjectSettings.customStylesheetText) {
      null -> ResourceProvider.Resource(emptyStylesheet)
      else -> ResourceProvider.Resource(text.toByteArray())
    }
  }

  override fun dispose() = Unit

  class Provider: MarkdownBrowserPreviewExtension.Provider {
    override fun createBrowserExtension(panel: MarkdownHtmlPanel): MarkdownBrowserPreviewExtension? {
      return InlineStylesExtension()
    }
  }

  companion object {
    private val emptyStylesheet = "".toByteArray()
  }
}
