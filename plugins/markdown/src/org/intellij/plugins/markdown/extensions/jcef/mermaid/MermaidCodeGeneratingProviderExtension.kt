// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.jcef.mermaid

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import org.intellij.markdown.ast.ASTNode
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.extensions.MarkdownCodeFenceCacheableProvider
import org.intellij.plugins.markdown.extensions.MarkdownExtensionWithExternalFiles
import org.intellij.plugins.markdown.extensions.jcef.MarkdownJCEFPreviewExtension
import org.intellij.plugins.markdown.ui.preview.html.MarkdownCodeFencePluginCacheCollector
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil
import org.intellij.plugins.markdown.ui.preview.ResourceProvider
import java.io.File
import java.nio.file.Paths

internal class MermaidCodeGeneratingProviderExtension(
  collector: MarkdownCodeFencePluginCacheCollector? = null
) : MarkdownCodeFenceCacheableProvider(collector),
    MarkdownJCEFPreviewExtension,
    MarkdownExtensionWithExternalFiles,
    ResourceProvider {
  override val scripts: List<String> = listOf(
    MAIN_SCRIPT_FILENAME,
    "mermaid/bootstrap.js"
  )

  override val styles: List<String> = listOf("mermaid/mermaid.css")

  override val events: Map<String, (String) -> Unit> =
    mapOf("storeMermaidFile" to this::storeFileEvent)

  override fun isApplicable(language: String) = isEnabled && isAvailable && language == "mermaid"

  override fun generateHtml(language: String, raw: String, node: ASTNode): String {
    val hash = MarkdownUtil.md5(raw, "")
    val key = getUniqueFile("mermaid", hash, "svg").toFile()
    if (key.exists()) {
      return "<img src=\"${key.toURI()}\"/>"
    }
    return "<div class=\"mermaid\" cache-id=\"$hash\" id=\"$hash\">$raw</div>"
  }

  fun store(key: String, content: ByteArray) {
    val actualKey = getUniqueFile("mermaid", key, "svg").toFile()
    FileUtil.createParentDirs(actualKey)
    actualKey.outputStream().buffered().use {
      it.write(content)
    }
    collector?.addAliveCachedFile(this, actualKey)
  }

  override fun onLAFChanged() = Unit

  override val displayName: String =
    MarkdownBundle.message("markdown.extensions.mermaid.display.name")

  override val id: String = "MermaidLanguageExtension"

  override val description: String = MarkdownBundle.message("markdown.extensions.mermaid.description")

  override val downloadLink: String = Registry.stringValue("markdown.mermaid.download.link")

  override val downloadFilename: String = "mermaid.js"

  override fun afterDownload(): Boolean {
    val targetFile = Paths.get(directory.toString(), "mermaid", downloadFilename).toFile()
    val sourceFile = File(directory, downloadFilename)
    sourceFile.copyTo(targetFile)
    return sourceFile.delete()
  }

  override fun canProvide(resourceName: String): Boolean {
    return resourceName in scripts || resourceName in styles
  }

  override fun loadResource(resourceName: String): ResourceProvider.Resource? {
    if (resourceName == MAIN_SCRIPT_FILENAME) {
      return ResourceProvider.loadExternalResource(File(directory, resourceName))
    }
    return ResourceProvider.loadInternalResource(this::class.java, resourceName)
  }

  override val resourceProvider: ResourceProvider = this

  private fun storeFileEvent(data: String) {
    if (data.isEmpty()) {
      return
    }
    val key = data.substring(0, data.indexOf(';'))
    val content = data.substring(data.indexOf(';') + 1)
    store(key, content.toByteArray())
  }

  companion object {
    private const val MAIN_SCRIPT_FILENAME = "mermaid/mermaid.js"
  }
}
