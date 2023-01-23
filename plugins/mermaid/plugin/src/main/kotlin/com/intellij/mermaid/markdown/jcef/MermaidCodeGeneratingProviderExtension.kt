package com.intellij.mermaid.markdown.jcef

import com.intellij.mermaid.lang.lexer.MermaidLexer
import com.intellij.mermaid.lang.lexer.MermaidTokenTypeSets
import com.intellij.mermaid.lang.lexer.MermaidTokens
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
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
    val text = removeIcons(raw)
    val hash = MarkdownUtil.md5(text, "") + determineTheme()
    val key = getUniqueFile("mermaid", hash, "svg").toFile()
    return when {
      key.exists() -> "<img src=\"${key.toURI()}\"/>"
      else -> createRawContentElement(hash, text)
    }
  }

  private fun removeIcons(raw: String): String {
    val lexer = MermaidLexer()
    lexer.start(raw)

    while (lexer.tokenType in MermaidTokenTypeSets.WHITE_SPACES || lexer.tokenType == MermaidTokens.OPEN_DIRECTIVE) {
      if (lexer.tokenType == MermaidTokens.OPEN_DIRECTIVE) {
        while (lexer.tokenType != MermaidTokens.CLOSE_DIRECTIVE) {
          lexer.advance()
        }
      }

      lexer.advance()
    }

    if (lexer.tokenType != MermaidTokens.Mindmap.MINDMAP) {
      return raw
    }

    val iconTokens = TokenSet.create(
      MermaidTokens.Mindmap.OPEN_ICON,
      MermaidTokens.Mindmap.ICON_VALUE,
      MermaidTokens.Mindmap.CLOSE_ICON
    )

    return buildList<Pair<String, IElementType?>> {
      add(lexer.tokenText to lexer.tokenType)

      while (lexer.tokenType != null) {
        lexer.advance()
        when (lexer.tokenType) {
          !in iconTokens -> add(lexer.tokenText to lexer.tokenType)

          MermaidTokens.Mindmap.OPEN_ICON -> {
            while (lastOrNull()?.second == MermaidTokens.WHITE_SPACE) {
              removeLast()
            }
            if (lastOrNull()?.second == MermaidTokens.EOL) {
              removeLast()
            }
          }
        }
      }
    }.joinToString(separator = "") { it.first }
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
