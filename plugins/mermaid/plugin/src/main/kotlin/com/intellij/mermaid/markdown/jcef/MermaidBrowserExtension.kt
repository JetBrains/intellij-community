package com.intellij.mermaid.markdown.jcef

import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.ResourceProvider

class MermaidBrowserExtension() : MarkdownBrowserPreviewExtension, ResourceProvider {

  override val scripts: List<String> = listOf(
    THEME_DEFINITION_FILENAME,
    "mermaid.js",
    "mermaid.js.map"
  )

  override val styles: List<String> = listOf("mermaid.css")

  override fun canProvide(resourceName: String): Boolean {
    return resourceName in scripts || resourceName in styles
  }

  override fun loadResource(resourceName: String): ResourceProvider.Resource? {
    return when (resourceName) {
      THEME_DEFINITION_FILENAME -> ResourceProvider.Resource(
        "window.mermaidTheme = '${MermaidCodeGeneratingProviderExtension.determineTheme()}';".toByteArray()
      )

      else -> ResourceProvider.loadInternalResource(this::class.java, resourceName)
    }
  }

  override val resourceProvider: ResourceProvider = this

  override fun dispose() = Unit

  class Provider : MarkdownBrowserPreviewExtension.Provider {
    override fun createBrowserExtension(panel: MarkdownHtmlPanel): MarkdownBrowserPreviewExtension {
      return MermaidBrowserExtension()
    }
  }

  companion object {
    private const val THEME_DEFINITION_FILENAME = "mermaid/themeDefinition.js"
  }
}
