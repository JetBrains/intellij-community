// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.markdown.jcef

import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.ResourceProvider

private const val THEME_DEFINITION_FILENAME = "mermaid/themeDefinition.js"

internal class MermaidBrowserExtension : MarkdownBrowserPreviewExtension, ResourceProvider {

  override val scripts: List<String> = listOf(
    THEME_DEFINITION_FILENAME,
    "mermaid.js",
  )

  override val styles: List<String> = listOf("mermaid.css")

  override fun canProvide(resourceName: String): Boolean {
    return resourceName in scripts || resourceName in styles || resourceName == "mermaid.js.map"
  }

  override fun loadResource(resourceName: String): ResourceProvider.Resource? {
    return when (resourceName) {
      THEME_DEFINITION_FILENAME -> ResourceProvider.Resource(
        "window.mermaidTheme = '${determineMermaidTheme()}';".toByteArray()
      )

      else -> ResourceProvider.loadInternalResource(this::class.java, resourceName)
    }
  }

  override val resourceProvider: ResourceProvider = this

  override fun dispose() {}

  class Provider : MarkdownBrowserPreviewExtension.Provider {
    override fun createBrowserExtension(panel: MarkdownHtmlPanel): MarkdownBrowserPreviewExtension {
      return MermaidBrowserExtension()
    }
  }
}
