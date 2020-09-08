// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.common.plantuml

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import org.intellij.markdown.ast.ASTNode
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.extensions.MarkdownCodeFenceCacheableProvider
import org.intellij.plugins.markdown.extensions.MarkdownCodeFencePluginGeneratingProvider
import org.intellij.plugins.markdown.extensions.MarkdownExtensionWithExternalFiles
import org.intellij.plugins.markdown.ui.preview.html.MarkdownCodeFencePluginCacheCollector
import java.io.File
import java.io.IOException
import java.net.URLClassLoader

internal class PlantUMLCodeGeneratingProvider(collector: MarkdownCodeFencePluginCacheCollector? = null)
  : MarkdownCodeFenceCacheableProvider(collector), MarkdownExtensionWithExternalFiles {
  override val downloadLink: String =
    Registry.stringValue("markdown.plantuml.download.link")

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

  override val displayName: String =
    MarkdownBundle.message("markdown.extensions.plantuml.display.name")

  override val description: String =
    MarkdownBundle.message("markdown.extensions.plantuml.description")

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
    private val LOG = Logger.getInstance(PlantUMLCodeFenceLanguageProvider::class.java)

    private val sourceStringReader by lazy {
      try {
        val self = MarkdownCodeFencePluginGeneratingProvider.all
          .filterIsInstance<PlantUMLCodeGeneratingProvider>()
          .first()
        Class.forName("net.sourceforge.plantuml.SourceStringReader", false, URLClassLoader(
          arrayOf(self.fullPath.toURI().toURL()), this::class.java.classLoader))
      }
      catch (e: Exception) {
        LOG.warn(
          "net.sourceforge.plantuml.SourceStringReader class isn't found in downloaded PlantUML jar. " +
          "Please try to download another PlantUML library version.", e)
        null
      }
    }

    private val generateImageMethod by lazy {
      try {
        sourceStringReader?.getDeclaredMethod("generateImage", Class.forName("java.io.OutputStream"))
      }
      catch (e: Exception) {
        LOG.warn(
          "'generateImage' method isn't found in the class 'net.sourceforge.plantuml.SourceStringReader'. " +
          "Please try to download another PlantUML library version.", e)
        null
      }
    }
  }

  private fun storeDiagram(source: String, file: File) {
    try {
      file.outputStream().buffered().use {
        generateImageMethod?.invoke(sourceStringReader?.getConstructor(String::class.java)?.newInstance(source), it)
      }
    }
    catch (e: Exception) {
      LOG.warn("Cannot save diagram PlantUML diagram. ", e)
    }
  }
}
