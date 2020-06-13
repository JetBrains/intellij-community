package org.intellij.plugins.markdown.extensions.plantuml

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import org.intellij.plugins.markdown.extensions.MarkdownCodeFenceCacheableProvider
import org.intellij.plugins.markdown.settings.MarkdownSettingsConfigurable
import org.intellij.plugins.markdown.ui.preview.MarkdownCodeFencePluginCacheCollector
import java.io.File
import java.io.IOException
import java.net.URLClassLoader

internal class PlantUMLCodeGeneratingProvider(collector: MarkdownCodeFencePluginCacheCollector?)
  : MarkdownCodeFenceCacheableProvider(collector) {
  // this empty constructor is needed for the component initialization
  constructor() : this(null)

  override fun isApplicable(language: String): Boolean {
    return ((language == "puml" || language == "plantuml") && MarkdownSettingsConfigurable.isPlantUMLAvailable())
  }

  override fun generateHtml(language: String, raw: String): String {
    val key = getUniqueFile(language, raw, "png").toFile()

    cacheDiagram(key, raw)
    collector?.addAliveCachedFile(this, key)

    return "<img src=\"${key.toURI()}\"/>"
  }

  override fun onLAFChanged() {}

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
        Class.forName("net.sourceforge.plantuml.SourceStringReader", false, URLClassLoader(
          arrayOf(MarkdownSettingsConfigurable.getDownloadedJarPath()?.toURI()?.toURL()), this::class.java.classLoader))
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
        sourceStringReader?.getDeclaredMethod("generateImage", Class.forName("java.io.File"))
      }
      catch (e: Exception) {
        LOG.warn(
          "'generateImage' method isn't found in the class 'net.sourceforge.plantuml.SourceStringReader'. " +
          "Please try to download another PlantUML library version.", e)
        null
      }
    }
  }

  private fun storeDiagram(source: String, fileName: File) {
    try {
      generateImageMethod?.invoke(sourceStringReader?.getConstructor(String::class.java)?.newInstance(source), fileName)
    }
    catch (e: Exception) {
      LOG.warn("Cannot save diagram PlantUML diagram. ", e)
    }
  }
}