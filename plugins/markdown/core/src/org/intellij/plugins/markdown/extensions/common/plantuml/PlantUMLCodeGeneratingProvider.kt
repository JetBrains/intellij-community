// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.common.plantuml

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import org.intellij.markdown.ast.ASTNode
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.extensions.MarkdownCodeFenceCacheableProvider
import org.intellij.plugins.markdown.extensions.MarkdownExtensionWithDownloadableFiles
import org.intellij.plugins.markdown.extensions.MarkdownExtensionWithDownloadableFiles.FileEntry
import org.intellij.plugins.markdown.ui.preview.html.MarkdownCodeFencePluginCacheCollector
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.io.IOException

@ApiStatus.Internal
class PlantUMLCodeGeneratingProvider(
  collector: MarkdownCodeFencePluginCacheCollector? = null
): MarkdownCodeFenceCacheableProvider(collector), MarkdownExtensionWithDownloadableFiles {
  override val externalFiles: Iterable<String>
    get() = ownFiles

  override val filesToDownload: Iterable<FileEntry>
    get() = dowloadableFiles

  override fun isApplicable(language: String): Boolean {
    return isEnabled && isAvailable && (language == "puml" || language == "plantuml")
  }

  override fun generateHtml(language: String, raw: String, node: ASTNode): String {
    val key = getUniqueFile(language, raw, "png").toFile()
    cacheDiagram(key, raw)
    collector?.addAliveCachedFile(this, key)
    return "<img src=\"${key.toURI()}\"/>"
  }

  override val displayName: String
    get() = MarkdownBundle.message("markdown.extensions.plantuml.display.name")

  override val description: String
    get() = MarkdownBundle.message("markdown.extensions.plantuml.description")

  override val id: String = "PlantUMLLanguageExtension"

  override fun beforeCleanup() {
    PlantUMLJarManager.getInstance().dropCache()
  }

  private fun cacheDiagram(path: File, text: String) {
    if (!path.exists()) {
      generateDiagram(text, path)
    }
  }

  @Throws(IOException::class)
  private fun generateDiagram(text: CharSequence, diagramPath: File) {
    var innerText: String = text.toString().trim()
    if (!innerText.startsWith("@startuml")) {
      innerText = "@startuml\n$innerText"
    }
    if (!innerText.endsWith("@enduml")) {
      innerText += "\n@enduml"
    }
    FileUtil.createParentDirs(diagramPath)
    storeDiagram(innerText, diagramPath)
  }

  companion object {
    const val jarFilename = "plantuml.jar"
    private val ownFiles = listOf(jarFilename)
    private val dowloadableFiles = listOf(FileEntry(jarFilename) { Registry.stringValue("markdown.plantuml.download.link") })

    private fun storeDiagram(source: String, file: File) {
      try {
        file.outputStream().buffered().use { PlantUMLJarManager.getInstance().generateImage(source, it) }
      } catch (exception: Exception) {
        thisLogger().warn("Cannot save diagram PlantUML diagram. ", exception)
      }
    }
  }
}
