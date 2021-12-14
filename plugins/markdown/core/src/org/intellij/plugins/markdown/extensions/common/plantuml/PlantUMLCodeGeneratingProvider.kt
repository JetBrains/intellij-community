// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.common.plantuml

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import org.intellij.markdown.ast.ASTNode
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.extensions.MarkdownCodeFenceCacheableProvider
import org.intellij.plugins.markdown.extensions.MarkdownExtensionWithExternalFiles
import org.intellij.plugins.markdown.ui.preview.html.MarkdownCodeFencePluginCacheCollector
import java.io.File
import java.io.IOException

internal class PlantUMLCodeGeneratingProvider(collector: MarkdownCodeFencePluginCacheCollector? = null):
  MarkdownCodeFenceCacheableProvider(collector),
  MarkdownExtensionWithExternalFiles
{
  override val downloadLink: String
    get() = Registry.stringValue("markdown.plantuml.download.link")

  override val downloadFilename: String = "plantuml.jar"

  override fun isApplicable(language: String): Boolean {
    return isEnabled && isAvailable && (language == "puml" || language == "plantuml")
  }

  override fun generateHtml(language: String, raw: String, node: ASTNode): String {
    val key = getUniqueFile(language, raw, "png").toFile()

    cacheDiagram(key, raw)
    collector?.addAliveCachedFile(this, key)

    return "<img src=\"${key.toURI()}\"/>"
  }

  override fun onLAFChanged() {}

  override val displayName: String
    get() = MarkdownBundle.message("markdown.extensions.plantuml.display.name")

  override val description: String
    get() = MarkdownBundle.message("markdown.extensions.plantuml.description")

  override val id: String = "PlantUMLLanguageExtension"

  private fun cacheDiagram(path: File, text: String) {
    if (!path.exists()) generateDiagram(text, path)
  }

  @Throws(IOException::class)
  private fun generateDiagram(text: CharSequence, diagramPath: File) {
    var innerText: String = text.toString().trim()
    if (!innerText.startsWith("@startuml")) innerText = "@startuml\n$innerText"
    if (!innerText.endsWith("@enduml")) innerText += "\n@enduml"

    FileUtil.createParentDirs(diagramPath)
    storeDiagram(innerText, diagramPath)
  }

  companion object {
    private fun storeDiagram(source: String, file: File) {
      try {
        file.outputStream().buffered().use { PlantUMLJarManager.getInstance().generateImage(source, it) }
      } catch (exception: Exception) {
        thisLogger().warn("Cannot save diagram PlantUML diagram. ", exception)
      }
    }
  }
}
