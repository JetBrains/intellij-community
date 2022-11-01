package com.intellij.mermaid.jcef

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.extensions.ExtensionsExternalFilesPathManager.Companion.obtainExternalFilesDirectoryPath
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.extensions.MarkdownExtensionWithDownloadableFiles
import org.intellij.plugins.markdown.extensions.MarkdownExtensionsUtil
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.ResourceProvider
import java.io.File

@Suppress("UnstableApiUsage")
class MermaidBrowserExtension(panel: MarkdownHtmlPanel, private val directory: File) : MarkdownBrowserPreviewExtension, ResourceProvider {
  init {
    panel.browserPipe?.subscribe(storeFileEventName, ::storeFileEvent)
    Disposer.register(this) {
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
    MAIN_SCRIPT_FILENAME,
    THEME_DEFINITION_FILENAME,
    "mermaid/bootstrap.js",
  )

  override val styles: List<String> = listOf("mermaid/mermaid.css")

  override fun canProvide(resourceName: String): Boolean {
    return resourceName in scripts || resourceName in styles
  }

  override fun loadResource(resourceName: String): ResourceProvider.Resource? {
    return when (resourceName) {
      MAIN_SCRIPT_FILENAME -> ResourceProvider.loadExternalResource(File(directory, resourceName))
      THEME_DEFINITION_FILENAME -> ResourceProvider.Resource(
        "window.mermaidTheme = '${MermaidCodeGeneratingProviderExtension.determineTheme()}';".toByteArray()
      )

      else -> ResourceProvider.loadInternalResource(this::class.java, resourceName)
    }
  }

  override val resourceProvider: ResourceProvider = this

  override fun dispose() = Unit

  class Provider : MarkdownBrowserPreviewExtension.Provider, MarkdownExtensionWithDownloadableFiles {
    override val displayName: String
      get() = MarkdownBundle.message("markdown.extensions.mermaid.display.name")

    override val id: String = "MermaidLanguageExtension"

    override val description: String
      get() = MarkdownBundle.message("markdown.extensions.mermaid.description")

    override val externalFiles: Iterable<String>
      get() = ownFiles

    override val filesToDownload: Iterable<MarkdownExtensionWithDownloadableFiles.FileEntry>
      get() = downloadableFiles

    override fun createBrowserExtension(panel: MarkdownHtmlPanel): MarkdownBrowserPreviewExtension? {
      return when {
        isEnabled -> MermaidBrowserExtension(panel, obtainExternalFilesDirectoryPath().toFile())
        else -> null
      }
    }
  }

  companion object {
    private const val MAIN_SCRIPT_FILENAME = "mermaid/mermaid.js"
    private const val THEME_DEFINITION_FILENAME = "mermaid/themeDefinition.js"
    private const val storeFileEventName = "storeMermaidFile"

    private val ownFiles = listOf(MAIN_SCRIPT_FILENAME)
    private val downloadableFiles =
      listOf(MarkdownExtensionWithDownloadableFiles.FileEntry(MAIN_SCRIPT_FILENAME) { Registry.stringValue("markdown.mermaid.download.link") })

    private val generatingProvider
      get() = MarkdownExtensionsUtil.findCodeFenceGeneratingProvider<MermaidCodeGeneratingProviderExtension>()
  }
}
