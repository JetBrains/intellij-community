// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.common.plantuml

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.util.registry.Registry
import org.intellij.markdown.ast.ASTNode
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.extensions.CodeFenceGeneratingProvider
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.extensions.MarkdownExtensionWithDownloadableFiles
import org.intellij.plugins.markdown.extensions.MarkdownExtensionWithDownloadableFiles.FileEntry
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil
import org.jetbrains.annotations.ApiStatus
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Base64

@ApiStatus.Internal
class PlantUMLCodeGeneratingProvider: CodeFenceGeneratingProvider, MarkdownExtensionWithDownloadableFiles, MarkdownBrowserPreviewExtension.Provider {
  private val cache = Caffeine.newBuilder().softValues().build<String, String>()

  override val externalFiles: Iterable<String>
    get() = ownFiles

  override val filesToDownload: Iterable<FileEntry>
    get() = dowloadableFiles

  override fun isApplicable(language: String): Boolean {
    return isEnabled && isAvailable && PlantUMLCodeFenceLanguageProvider.isPlantUmlInfoString(language.lowercase())
  }

  override fun generateHtml(language: String, raw: String, node: ASTNode): String {
    val content = obtainGeneratedContent(raw)
    val header = "data:image/png;base64,"
    return """<img src="$header$content" from-extension=true/>"""
  }

  // Not thread safe
  private fun obtainGeneratedContent(raw: String): String {
    val key = MarkdownUtil.md5(raw, "salt")
    val cached = cache.getIfPresent(key)
    if (cached != null) {
      return cached
    }
    val generated = generateDiagram(raw)
    cache.put(key, generated)
    return generated
  }

  override val displayName: String
    get() = MarkdownBundle.message("markdown.extensions.plantuml.display.name")

  override val description: String
    get() = MarkdownBundle.message("markdown.extensions.plantuml.description")

  override val id: String = "PlantUMLLanguageExtension"

  override fun beforeCleanup() {
    PlantUMLJarManager.getInstance().dropCache()
  }

  /**
   * PlantUML support doesn't currently require any actions/resources inside an actual browser.
   * This implementation is not registered in plugin.xml and is needed to make sure that
   * PlantUML support extension is treated the same way as other browser extensions (like Mermaid.js one).
   *
   * Such code can be found mostly in [org.intellij.plugins.markdown.settings.MarkdownSettingsConfigurable].
   */
  override fun createBrowserExtension(panel: MarkdownHtmlPanel): MarkdownBrowserPreviewExtension? {
    return null
  }

  @Throws(IOException::class)
  private fun generateDiagram(text: CharSequence): String {
    val content = buildString {
      if (!text.startsWith("@startuml")) {
        append("@startuml\n")
      }
      append(text)
      if (!text.endsWith("@enduml")) {
        append("\n@enduml")
      }
    }
    val stream = ByteArrayOutputStream()
    PlantUMLJarManager.getInstance().generateImage(content, stream)
    val encodedContent = Base64.getEncoder().encode(stream.toByteArray())
    return encodedContent.toString(Charsets.UTF_8)
  }

  companion object {
    const val jarFilename = "plantuml.jar"
    private val ownFiles = listOf(jarFilename)
    private val dowloadableFiles = listOf(FileEntry(jarFilename) { Registry.stringValue("markdown.plantuml.download.link") })
  }
}
