// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.common.highlighter

import com.intellij.openapi.vfs.VirtualFile
import org.intellij.markdown.ast.ASTNode
import org.intellij.plugins.markdown.extensions.CodeFenceGeneratingProvider
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.CommandRunnerExtension
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser
import org.intellij.plugins.markdown.ui.preview.html.DefaultCodeFenceGeneratingProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MarkdownCodeFencePreviewHighlighter: CodeFenceGeneratingProvider {
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

  override fun generateHtml(language: String, raw: String, node: ASTNode): String {
    val injectedLanguage = CodeFenceLanguageGuesser.guessLanguageForInjection(language)
    if (injectedLanguage == null) {
      return DefaultCodeFenceGeneratingProvider.escape(raw)
    }
    val cacheKey = cacheManager.obtainCacheKey(raw, language)
    val cached = cacheManager.obtainCachedHtml(cacheKey)
    if (cached != null) {
      return cached
    }
    val highlightedRanges = collectHighlightedChunks(injectedLanguage, raw)
    val text = buildHighlightedFenceContent(
      raw,
      highlightedRanges,
      node,
      useAbsoluteOffsets = true,
      additionalLineProcessor = ::processCodeLine
    )
    cacheManager.cacheHtml(cacheKey, text)
    return text
  }

  private fun processCodeLine(line: String): String {
    val file = currentFile.get() ?: return ""
    val runner = CommandRunnerExtension.getRunnerByFile(file)
    return runner?.processCodeLine(line, true).orEmpty()
  }
}
