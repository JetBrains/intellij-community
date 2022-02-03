// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.jcef.mermaid

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.extensions.*
import org.intellij.plugins.markdown.extensions.ExtensionsExternalFilesPathManager.Companion.obtainExternalFilesDirectoryPath
import org.intellij.plugins.markdown.extensions.MarkdownExtensionWithDownloadableFiles.FileEntry
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.ResourceProvider
import org.jetbrains.annotations.ApiStatus
import java.io.File

/**
 * A brief description of how this extension works.
 *
 * Since we can generate an actual diagram image only on the browser side, we have to do some trickery.
 *
 * So, there are two cases to consider (see [MermaidCodeGeneratingProviderExtension.generateHtml]):
 *  * Current code fence content is totally new and does not have any cached images;
 *  * Current code fence does have a cached image.
 *
 * Let's take a look at the first case. We generate a unique `cacheId` for the current fence content,
 * then insert a plain div with an actual fence content and `cacheId` attribute. On the browser side
 * we take that content and generate an actual image. To store this image in the cache on the IDE side,
 * we send it back along with it's `cacheId`. Since our message passing interface only allows to send plain strings,
 * we simply prepend `cacheId` value with the semicolon to the actual image content (see [MermaidBrowserExtension.storeFileEvent]).
 * (To be precise, we do also add current IDE theme id to the hash, so our cached images could maintain
 * relation with IDE theme they were generated for, see [MermaidCodeGeneratingProviderExtension.store]).
 *
 * The second case is much easier: we simply insert an `<img>` tag with the url to the cached image.
 */
@ApiStatus.Internal
class MermaidBrowserExtension(panel: MarkdownHtmlPanel, private val directory: File): MarkdownBrowserPreviewExtension, ResourceProvider {
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

  class Provider: MarkdownBrowserPreviewExtension.Provider, MarkdownExtensionWithDownloadableFiles {
    override val displayName: String
      get() = MarkdownBundle.message("markdown.extensions.mermaid.display.name")

    override val id: String = "MermaidLanguageExtension"

    override val description: String
      get() = MarkdownBundle.message("markdown.extensions.mermaid.description")

    override val externalFiles: Iterable<String>
      get() = ownFiles

    override val filesToDownload: Iterable<FileEntry>
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
    private val downloadableFiles = listOf(FileEntry(MAIN_SCRIPT_FILENAME) { Registry.stringValue("markdown.mermaid.download.link") })

    private val generatingProvider
      get() = MarkdownExtensionsUtil.findCodeFenceGeneratingProvider<MermaidCodeGeneratingProviderExtension>()
  }
}
