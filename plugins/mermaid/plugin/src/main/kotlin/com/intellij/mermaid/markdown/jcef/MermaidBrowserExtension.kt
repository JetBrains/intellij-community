package com.intellij.mermaid.markdown.jcef

import com.intellij.openapi.util.Disposer
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.extensions.MarkdownExtensionsUtil
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.ResourceProvider

class MermaidBrowserExtension(panel: MarkdownHtmlPanel) : MarkdownBrowserPreviewExtension, ResourceProvider {
  init {
    @Suppress("UnstableApiUsage")
    panel.browserPipe?.subscribe(storeFileEventName, ::storeFileEvent)
    Disposer.register(this) {
      @Suppress("UnstableApiUsage")
      panel.browserPipe?.removeSubscription(storeFileEventName, ::storeFileEvent)
    }
  }

  private fun storeFileEvent(data: String) {
    if (data.isEmpty()) {
      return
    }
    val key = data.substring(0, data.indexOf(';'))
    val content = data.substring(data.indexOf(';') + 1)
    generatingProvider?.store(key, content.toByteArray())
  }

  override val scripts: List<String> = listOf(
    THEME_DEFINITION_FILENAME,
    "extension.js",
    "extension.js.map",
    "10.js",
    "10.js.map"
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
      return MermaidBrowserExtension(panel)
    }
  }

  companion object {
    private const val THEME_DEFINITION_FILENAME = "mermaid/themeDefinition.js"
    private const val storeFileEventName = "storeMermaidFile"

    @Suppress("UnstableApiUsage")
    private val generatingProvider
      get() = MarkdownExtensionsUtil.findCodeFenceGeneratingProvider<MermaidCodeGeneratingProviderExtension>()
  }
}
