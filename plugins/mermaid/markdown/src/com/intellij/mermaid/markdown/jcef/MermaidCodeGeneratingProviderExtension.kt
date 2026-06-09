// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.markdown.jcef

import com.intellij.mermaid.lang.lexer.MermaidLexer
import com.intellij.mermaid.lang.lexer.MermaidTokenTypeSets
import com.intellij.mermaid.lang.lexer.MermaidTokens
import com.intellij.mermaid.settings.MermaidSettings
import com.intellij.mermaid.settings.MermaidSettingsState
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.ui.ColorUtil
import org.intellij.markdown.ast.ASTNode
import org.intellij.plugins.markdown.extensions.CodeFenceGeneratingProvider
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil
import java.util.Base64

@Suppress("UnstableApiUsage")
internal class MermaidCodeGeneratingProviderExtension : CodeFenceGeneratingProvider {
  override fun isApplicable(language: String): Boolean {
    return language == "mermaid" || isCustomMermaidInfoString(language)
  }

  override fun generateHtml(language: String, raw: String, node: ASTNode): String {
    val content = removeIcons(raw)
    val hash = MarkdownUtil.md5(content, "") + determineMermaidTheme()
    return """
      <div class="mermaid" data-cache-id="$hash" id="$hash" data-actual-fence-content="${escapeContent(content)}"></div>
    """.trimIndent()
  }

  private fun removeIcons(raw: String): String {
    val lexer = MermaidLexer()
    lexer.start(raw)

    while (lexer.tokenType in MermaidTokenTypeSets.WHITE_SPACES || lexer.tokenType == MermaidTokens.Directives.OPEN_DIRECTIVE) {
      if (lexer.tokenType == MermaidTokens.Directives.OPEN_DIRECTIVE) {
        while (lexer.tokenType != null && lexer.tokenType != MermaidTokens.Directives.CLOSE_DIRECTIVE) {
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

  private fun escapeContent(content: String): String {
    return String(Base64.getEncoder().encode(content.toByteArray()))
  }
}

internal fun determineMermaidTheme(): String {
  val mermaidSettings = MermaidSettings.getInstance()
  val theme = mermaidSettings.theme
  if (theme == MermaidSettingsState.Theme.FOLLOW_IDE) {
    val scheme = EditorColorsManager.getInstance().globalScheme
    return when {
      ColorUtil.isDark(scheme.defaultBackground) -> "dark"
      else -> "default"
    }
  }
  return theme.value
}