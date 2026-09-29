// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.common.plantuml

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.progress.util.awaitWithCheckCanceled
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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.future.asCompletableFuture
import java.util.Base64
import java.util.concurrent.CompletableFuture

@ApiStatus.Internal
class PlantUMLCodeGeneratingProvider: CodeFenceGeneratingProvider, MarkdownExtensionWithDownloadableFiles, MarkdownBrowserPreviewExtension.Provider {
  private val cache = Caffeine.newBuilder().softValues().build<String, CompletableFuture<String>>()

  override val externalFiles: Iterable<String>
    get() = ownFiles

  override val filesToDownload: Iterable<FileEntry>
    get() = downloadableFiles

  override fun isApplicable(language: String): Boolean {
    return isEnabled && isAvailable && PlantUMLCodeFenceLanguageProvider.isPlantUmlInfoString(language.lowercase())
  }

  override fun generateHtml(language: String, raw: String, node: ASTNode): String {
    val content = obtainGeneratedContent(raw)
    val header = "data:image/png;base64,"
    return """<img src="$header$content" from-extension=true/>"""
  }

  private fun obtainGeneratedContent(raw: String): String {
    val key = MarkdownUtil.md5(raw, "salt")
    return cache.get(key) { generateDiagram(raw).asCompletableFuture() }.awaitWithCheckCanceled()
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

  private fun generateDiagram(text: CharSequence): Deferred<String> {
    val content = buildString {
      if (!text.startsWith("@startuml")) {
        append("@startuml\n")
      }
      append(text)
      if (!text.endsWith("@enduml")) {
        append("\n@enduml")
      }
    }
    return PlantUMLJarManager.getInstance().generateImage(content, Base64.getEncoder()::encodeToString)
  }

  companion object {
    const val jarFilename = "plantuml.jar"
    private val ownFiles = listOf(jarFilename)
    private val downloadableFiles = listOf(FileEntry(jarFilename) { Registry.stringValue("markdown.plantuml.download.link") })
  }
}
