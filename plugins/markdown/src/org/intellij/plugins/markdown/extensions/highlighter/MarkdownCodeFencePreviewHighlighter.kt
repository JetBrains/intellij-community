// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.highlighter

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.testFramework.LightVirtualFile
import org.intellij.markdown.html.entities.EntityConverter
import org.intellij.plugins.markdown.extensions.MarkdownCodeFencePluginGeneratingProvider
import org.intellij.plugins.markdown.injection.alias.LanguageGuesser
import org.intellij.plugins.markdown.ui.preview.MarkdownUtil
import java.awt.Color
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

  override fun generateHtml(language: String, raw: String): String {
    val lang = LanguageGuesser.guessLanguageForInjection(language) ?: return escape(raw)

    val md5 = MarkdownUtil.md5(raw, language)

    val cached = values[md5]

    val resolved = cached?.resolve()
    if (resolved != null) {
      cached.expires += expiration
      return resolved.html
    }

    cleanup()

    val text = render(lang, raw)
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

  private fun render(lang: Language, text: String): String {
    val file = LightVirtualFile("markdown_temp", text)

    val highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(lang, null, file)
    val lexer = highlighter.highlightingLexer

    lexer.start(text)

    val html = StringBuilder(text.length)

    while (lexer.tokenType != null) {
      val type = lexer.tokenType
      val highlights = highlighter.getTokenHighlights(type).lastOrNull()
      val color = highlights?.defaultAttributes?.foregroundColor

      val current = if (color != null) {
        //deprecated font tag is used since JavaFX HTML Viewer does not support span tag with style
        "<font color=\"${color.toHex()}\">${escape(lexer.tokenText)}</font>"
      }
      else escape(lexer.tokenText)

      html.append(current)
      lexer.advance()
    }

    return html.toString()
  }

  private fun Color.toHex(): String = String.format("#%02x%02x%02x", red, green, blue)

  private fun escape(html: String) = EntityConverter.replaceEntities(html, processEntities = true, processEscapes = true)
}