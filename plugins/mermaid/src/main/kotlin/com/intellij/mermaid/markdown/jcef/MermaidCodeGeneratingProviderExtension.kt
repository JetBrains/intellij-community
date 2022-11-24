package com.intellij.mermaid.markdown.jcef

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ColorUtil
import org.intellij.markdown.ast.ASTNode
import org.intellij.plugins.markdown.extensions.MarkdownCodeFenceCacheableProvider
import org.intellij.plugins.markdown.ui.preview.html.MarkdownCodeFencePluginCacheCollector
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil
import java.util.*

@Suppress("UnstableApiUsage")
internal class MermaidCodeGeneratingProviderExtension(collector: MarkdownCodeFencePluginCacheCollector? = null) :
  MarkdownCodeFenceCacheableProvider(collector) {
  override fun isApplicable(language: String): Boolean {
    return language == "mermaid"
  }

  override fun generateHtml(language: String, raw: String, node: ASTNode): String {
    val hash = MarkdownUtil.md5(raw, "") + determineTheme()
    val key = getUniqueFile("mermaid", hash, "svg").toFile()
    return when {
      key.exists() -> "<img src=\"${key.toURI()}\"/>"
      else -> createRawContentElement(hash, raw)
    }
  }

  fun store(key: String, content: ByteArray) {
    val actualKey = getUniqueFile("mermaid", key, "svg").toFile()
    FileUtil.createParentDirs(actualKey)
    actualKey.outputStream().buffered().use {
      it.write(content)
    }
    collector?.addAliveCachedFile(this, actualKey)
  }

  private fun escapeContent(content: String): String {
    return String(Base64.getEncoder().encode(content.toByteArray()))
  }

  private fun createRawContentElement(hash: String, content: String): String {
    return "<div class=\"mermaid\" data-cache-id=\"$hash\" id=\"$hash\" data-actual-fence-content=\"${escapeContent(content)}\"></div>"
  }

  companion object {
    fun determineTheme(): String {
      val registryValue = Registry.stringValue("markdown.mermaid.theme")
      if (registryValue == "follow-ide") {
        val scheme = EditorColorsManager.getInstance().globalScheme
        return when {
          ColorUtil.isDark(scheme.defaultBackground) -> "dark"
          else -> "default"
        }
      }
      return registryValue
    }
  }
}
