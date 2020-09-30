// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.common.highlighter

import com.intellij.lang.Language
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.ColorUtil
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.plugins.markdown.extensions.MarkdownCodeFencePluginGeneratingProvider
import org.intellij.plugins.markdown.injection.alias.LanguageGuesser
import org.intellij.plugins.markdown.ui.preview.html.MarkdownCodeFenceGeneratingProvider
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil
import java.lang.ref.SoftReference
import java.util.concurrent.ConcurrentHashMap

internal class MarkdownCodeFencePreviewHighlighter : MarkdownCodeFencePluginGeneratingProvider {
  companion object {
    private const val expiration = 5 * 60 * 1000
  }

  /**
   * HTML generated for this CodeFence and allocated in memory by SoftReference
   *
   * All expired entities (System.currentTimeMillis() > expires) will be removed on update of cache
   *
   * [html] is referenced by SoftReference to be sure that cache will be removed from memory if JVM
   * need more memory
   */
  private data class CachedHTMLResult(val html: SoftReference<String>, var expires: Long) {
    fun resolve() = html.get()?.let { HTMLResult(it, expires) }

    data class HTMLResult(val html: String, val expires: Long)
  }

  private val values = ConcurrentHashMap<String, CachedHTMLResult>()

  override fun isApplicable(language: String): Boolean {
    return LanguageGuesser.guessLanguageForInjection(language) != null
  }

  override fun generateHtml(language: String, raw: String, node: ASTNode): String {
    val lang = LanguageGuesser.guessLanguageForInjection(language) ?: return MarkdownCodeFenceGeneratingProvider.escape(raw)

    val md5 = MarkdownUtil.md5(raw, language)

    val cached = values[md5]

    val resolved = cached?.resolve()
    if (resolved != null) {
      cached.expires += expiration
      return resolved.html
    }

    cleanup()

    val text = render(lang, raw, node)
    val html = CachedHTMLResult(SoftReference(text), System.currentTimeMillis() + expiration)

    values[md5] = html

    return text
  }

  override fun onLAFChanged() {
    values.clear()
  }

  private fun cleanup() {
    val time = System.currentTimeMillis()

    val toRemove = values.filter { it.value.expires < time }.keys
    toRemove.forEach { values.remove(it) }
  }

  private fun render(lang: Language, text: String, node: ASTNode): String {
    val highlightTokens = collectHighlightTokens(lang, text)
    // Mannually walk over each line and recalculate line offsets
    val baseOffset = MarkdownCodeFenceGeneratingProvider.calcCodeFenceContentBaseOffset(node)
    val lines = ArrayList<String>()
    var left = 0
    for (line in text.lines()) {
      val targets = highlightTokens.entries.filter { (range, _) ->
        range.first >= left && range.last <= left + line.length
      }.map { (range, replacement) -> (range.shift(-left)) to replacement }
      val right = left + line.length + 1
      lines.add(buildString {
        append("<span ${HtmlGenerator.SRC_ATTRIBUTE_NAME}='${left + baseOffset}..${right + baseOffset}'>")
        appendWithReplacements(line, targets, this)
        append("</span>")
      })
      left = right
    }
    return lines.joinToString(
      separator = "\n",
      prefix = "<span ${HtmlGenerator.SRC_ATTRIBUTE_NAME}='${node.startOffset}..${baseOffset}'/>",
      postfix = node.children.find { it.type == MarkdownTokenTypes.CODE_FENCE_END }?.let {
        "<span ${HtmlGenerator.SRC_ATTRIBUTE_NAME}='${it.startOffset}..${it.endOffset}'/>"
      } ?: ""
    )
  }

  private fun collectHighlightTokens(lang: Language, text: String): Map<IntRange, String> {
    val file = LightVirtualFile("markdown_temp", text)
    val highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(lang, null, file)
    val lexer = highlighter.highlightingLexer
    lexer.start(text)
    val colorScheme = EditorColorsManager.getInstance().globalScheme
    // Collect all tokens that needs to be highlighted
    val highlightTokens = hashMapOf<IntRange, String>()
    while (lexer.tokenType != null) {
      val type = lexer.tokenType
      val highlights = highlighter.getTokenHighlights(type).lastOrNull()
      val color = highlights?.let {
        colorScheme.getAttributes(it)?.foregroundColor
      } ?: highlights?.defaultAttributes?.foregroundColor
      if (color != null) {
        highlightTokens[lexer.tokenStart..lexer.tokenEnd] =
          "<span style=\"color:${ColorUtil.toHtmlColor(color)}\">${MarkdownCodeFenceGeneratingProvider.escape(lexer.tokenText)}</span>"
      }
      lexer.advance()
    }
    return highlightTokens
  }

  private fun IntRange.shift(value: Int): IntRange = (first + value)..(last + value)

  private fun appendWithReplacements(line: String, targets: List<Pair<IntRange, String>>, builder: StringBuilder) {
    var actualLine = line
    var left = 0
    for ((range, replacement) in targets.sortedBy { it.first.first }) {
      builder.append(MarkdownCodeFenceGeneratingProvider.escape(actualLine.substring(0, range.first - left)))
      builder.append(replacement)
      actualLine = actualLine.substring(range.last - left)
      left = range.last
    }
    builder.append(MarkdownCodeFenceGeneratingProvider.escape(actualLine))
  }
}
