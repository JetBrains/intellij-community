// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.common.highlighter

import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.markdown.ast.ASTNode
import org.intellij.plugins.markdown.extensions.CodeFenceGeneratingProvider
import org.intellij.plugins.markdown.extensions.jcef.MarkdownASTNode
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.CommandRunnerExtension
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.intellij.plugins.markdown.ui.preview.html.DefaultCodeFenceGeneratingProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MarkdownCodeFencePreviewHighlighter: CodeFenceGeneratingProvider, Disposable {
  private val cacheManager
    get() = HtmlCacheManager.getInstance()

  private val currentFile = ThreadLocal<VirtualFile?>()

  override fun isApplicable(language: String): Boolean {
    return CodeFenceLanguageGuesser.guessLanguageForInjection(language) != null
  }

  fun generateHtmlForFile(language: String, raw: String, node: ASTNode, file: VirtualFile): String {
    currentFile.set(file)
    val result = generateHtml(language, raw, node)
    currentFile.set(null)
    return result
  }

  // This is a checksum designed to match one generated in JavaScript for the same raw code.
  private fun checksum53(input: String): String {
      var h1 = 0xDEADBEEF.toInt()
      var h2 = 0x41C6CE57

      val normalizedInput = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFC)

      for (ch in normalizedInput) {
          h1 = (h1 xor ch.code) * -1640531535
          h2 = (h2 xor ch.code) * 1597334677
      }

      h1 = ((h1 xor (h1 ushr 16)) * -2048144789) xor
           ((h2 xor (h2 ushr 13)) * -1028477387)
      h2 = ((h2 xor (h2 ushr 16)) * -2048144789) xor
           ((h1 xor (h1 ushr 13)) * -1028477387)

      val result = 4294967296 * (0x1FFFFF and h2) + (h1.toLong() and 0xFFFFFFFFL)

      return result.toString(16).uppercase().padStart(14, '0')
  }

  override fun generateHtml(language: String, raw: String, node: ASTNode): String {
    val project = (node as? MarkdownASTNode)?.project
    var keyExtra = ""
    var flexibleAboutLanguages = false

    if (project != null) {
      val settings = project.let(MarkdownSettings::getInstance)
      keyExtra = ";${settings.style};${settings.useGitHubSyntaxColors}}"
      flexibleAboutLanguages = settings.useAlternativeHighlighting
    }

    var injectedLanguage = CodeFenceLanguageGuesser.guessLanguageForInjection(language)

    if (injectedLanguage == null) {
      if (flexibleAboutLanguages && language.isNotEmpty() &&
          !Regex("""text|plain""").containsMatchIn(language.lowercase())) {
        injectedLanguage = Language.ANY
      }
      else {
        return DefaultCodeFenceGeneratingProvider.escape(raw)
      }
    }

    val cacheKey = cacheManager.obtainCacheKey(raw + keyExtra, language)
    val cached = cacheManager.obtainCachedHtml(cacheKey)

    if (cached != null) {
      return cached
    }

    val highlightedRanges = collectHighlightedChunks(injectedLanguage!!, raw, project, language)
    val text: String

    if (highlightedRanges.isEmpty()) {
      return DefaultCodeFenceGeneratingProvider.escape(raw)
    }
    else {
      text = buildHighlightedFenceContent(
        raw,
        highlightedRanges,
        node,
        useAbsoluteOffsets = true,
        additionalLineProcessor = ::processCodeLine
      )
    }

    cacheManager.cacheHtml(cacheKey, text)
    codeTracking.getOrPut(checksum53(raw)) { CodeTrackingForJS(cacheKey) }.previewers.add(this)

     return text
  }

  private fun processCodeLine(line: String): String {
    val file = currentFile.get() ?: return ""
    val runner = CommandRunnerExtension.getRunnerByFile(file)
    return runner?.processCodeLine(line, true).orEmpty()
  }

  override fun dispose() {
    extraCacheCleanup(this)
  }

  private data class CodeTrackingForJS(val cacheKey: String,
                                       val previewers: MutableSet<MarkdownCodeFencePreviewHighlighter> = mutableSetOf())

  companion object {
    private val codeTracking = HashMap<String, CodeTrackingForJS>()
    private var cleanupCounter = 0

    internal fun updateCodeFenceCache(javaScriptChecksum: String, newContent: String) {
      val match = codeTracking.get(javaScriptChecksum) ?: return

      match.previewers.forEach { it.cacheManager.cacheHtml(match.cacheKey, newContent) }

      if (cleanupCounter++ > 1000) {
        periodicCleanup()
      }
    }

    internal fun extraCacheCleanup(previewer: MarkdownCodeFencePreviewHighlighter) {
      codeTracking.forEach { (key, value) ->
        value.previewers.remove(previewer)

        if (value.previewers.isEmpty()) {
          codeTracking.remove(key)
        }
      }

      periodicCleanup()
    }

    private fun periodicCleanup() {
      codeTracking.forEach { (key, value) ->
        for (i in value.previewers.indices.reversed()) {
          val preview = value.previewers.elementAt(i)

          if (!preview.cacheManager.hasKey(value.cacheKey))
            value.previewers.remove(preview)
        }

        if (value.previewers.isEmpty()) {
          codeTracking.remove(key)
        }

         cleanupCounter = 0
      }
    }
  }
}
