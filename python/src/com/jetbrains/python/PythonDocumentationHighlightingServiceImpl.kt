// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.lang.documentation.QuickDocHighlightingHelper
import com.intellij.lang.documentation.QuickDocHighlightingHelper.getStyledCodeFragment
import com.intellij.lang.documentation.QuickDocHighlightingHelper.guessLanguage
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.ThrowableComputable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jsoup.Jsoup

@ApiStatus.Internal
class PythonDocumentationHighlightingServiceImpl : PythonDocumentationHighlightingService() {
  private val LOG: Logger = Logger.getInstance(PythonDocumentationHighlightingServiceImpl::class.java)

  override fun highlightedCodeSnippet(project: Project, codeSnippet: String): String {
    return QuickDocHighlightingHelper.getStyledCodeFragment(project, PythonLanguage.INSTANCE, codeSnippet)
  }

  override fun styledSpan(textAttributeKey: TextAttributesKey, text: String): String {
    return QuickDocHighlightingHelper.getStyledFragment(text, textAttributeKey)
  }

  override fun highlightCodeBlockInHtml(project: Project, @Nls codeBlock: String): String {
    if (codeBlock.isBlank()) return codeBlock

    return runCatching {
      val document = Jsoup.parse(codeBlock)

      val preElements = document.select("pre[data-language]")

      for (preElement in preElements) {
        val codeElement = preElement.selectFirst("code") ?: continue

        val codeText = codeElement.wholeText()
        if (codeText.isBlank()) continue

        val languageName = preElement.attr("data-language")
        val language = guessLanguage(languageName) ?: continue

        val highlightedHtml = ReadAction.compute<@NlsSafe String, RuntimeException?>(ThrowableComputable { getStyledCodeFragment(project, language, codeText) })
        codeElement.html(highlightedHtml)
      }
      document.body().html()
    }.getOrElse {
      LOG.debug("Failed to apply syntax highlighting to code block in rendered documentation", it)
      codeBlock
    }
  }
}
