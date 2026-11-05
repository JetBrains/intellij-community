// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.common.highlighter

import org.intellij.markdown.ast.ASTNode
import org.intellij.plugins.markdown.extensions.CodeFenceGeneratingProvider
import org.intellij.plugins.markdown.injection.aliases.CodeFenceLanguageGuesser
import org.intellij.plugins.markdown.ui.preview.html.DefaultCodeFenceGeneratingProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MarkdownCodeFencePreviewHighlighter: CodeFenceGeneratingProvider {
  private val cacheManager
    get() = HtmlCacheManager.getInstance()

  override fun isApplicable(language: String): Boolean {
    return CodeFenceLanguageGuesser.guessLanguageForInjection(language) != null
  }

  override fun generateHtml(language: String, raw: String, node: ASTNode): String {
    return generateHtml(language, raw, node, lineProcessor = null)
  }

  fun generateHtml(language: String, raw: String, node: ASTNode, lineProcessor: ((String) -> String)?): String {
    val injectedLanguage = CodeFenceLanguageGuesser.guessLanguageForInjection(language)
    if (injectedLanguage == null) {
      return DefaultCodeFenceGeneratingProvider.escape(raw)
    }
    val cacheKey = cacheManager.obtainCacheKey(raw, language)
    val highlightedRanges = cacheManager.obtainCachedRanges(cacheKey)
                            ?: collectHighlightedChunks(injectedLanguage, raw).also { cacheManager.cacheRanges(cacheKey, it) }
    return buildHighlightedFenceContent(
      raw,
      highlightedRanges,
      node,
      useAbsoluteOffsets = true,
      additionalLineProcessor = lineProcessor
    )
  }
}
