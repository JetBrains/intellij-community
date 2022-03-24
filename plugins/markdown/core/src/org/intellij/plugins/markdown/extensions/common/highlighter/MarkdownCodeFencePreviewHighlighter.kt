// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.common.highlighter

import com.intellij.lang.Language
import com.intellij.markdown.utils.lang.HtmlSyntaxHighlighter
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColorUtil
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.plugins.markdown.extensions.CodeFenceGeneratingProvider
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.CommandRunnerExtension
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser
import org.intellij.plugins.markdown.ui.preview.html.DefaultCodeFenceGeneratingProvider
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil
import java.lang.ref.SoftReference
import java.util.concurrent.ConcurrentHashMap

internal class MarkdownCodeFencePreviewHighlighter : CodeFenceGeneratingProvider {
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
  private val currentFile: ThreadLocal<VirtualFile?> = ThreadLocal()

  override fun isApplicable(language: String): Boolean {
    return CodeFenceLanguageGuesser.guessLanguageForInjection(language) != null
  }

  fun generateHtmlForFile(language: String, raw: String, node: ASTNode, file: VirtualFile): String {
    currentFile.set(file)
    val result = generateHtml(language, raw, node)
    currentFile.set(null)
    return result
  }

  override fun generateHtml(language: String, raw: String, node: ASTNode): String {
    val lang = CodeFenceLanguageGuesser.guessLanguageForInjection(language) ?: return DefaultCodeFenceGeneratingProvider.escape(raw)

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

  override fun onLaFChanged() {
    values.clear()
  }

  private fun cleanup() {
    val time = System.currentTimeMillis()

    val toRemove = values.filter { it.value.expires < time }.keys
    toRemove.forEach { values.remove(it) }
  }

  private fun render(lang: Language, text: String, node: ASTNode): String {
    val highlightTokens = mutableMapOf<IntRange, String>()
    HtmlSyntaxHighlighter.parseContent(null, lang, text) { content, intRange, color ->
      if (color != null) {
        highlightTokens[intRange] = "<span style=\"color:${ColorUtil.toHtmlColor(color)}\">${
          DefaultCodeFenceGeneratingProvider.escape(content)
        }</span>"
      }
    }

    // Mannually walk over each line and recalculate line offsets
    val baseOffset = DefaultCodeFenceGeneratingProvider.calcCodeFenceContentBaseOffset(node)
    val lines = ArrayList<String>()
    var left = 0
    for (line in text.lines()) {
      val targets = highlightTokens.entries.filter { (range, _) ->
        range.first >= left && range.last <= left + line.length
      }.map { (range, replacement) -> (range.shift(-left)) to replacement }
      val right = left + line.length + 1
      lines.add(buildString {
        append("<span ${HtmlGenerator.SRC_ATTRIBUTE_NAME}='${left + baseOffset}..${right + baseOffset}'>")
        if (lines.isNotEmpty()) {
          // skip first line processing since there is always run marker for whole block
          append(processCodeLine(line))
        }
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

  private fun IntRange.shift(value: Int): IntRange = (first + value)..(last + value)

  private fun appendWithReplacements(line: String, targets: List<Pair<IntRange, String>>, builder: StringBuilder) {
    var actualLine = line
    var left = 0
    for ((range, replacement) in targets.sortedBy { it.first.first }) {
      builder.append(DefaultCodeFenceGeneratingProvider.escape(actualLine.substring(0, range.first - left)))
      builder.append(replacement)
      actualLine = actualLine.substring(range.last - left)
      left = range.last
    }
    builder.append(DefaultCodeFenceGeneratingProvider.escape(actualLine))
  }

  private fun processCodeLine(rawCodeLine: String): String = currentFile.get()?.let { file ->
    CommandRunnerExtension.getRunnerByFile(file)?.processCodeLine(rawCodeLine, true)
  } ?: ""
}
