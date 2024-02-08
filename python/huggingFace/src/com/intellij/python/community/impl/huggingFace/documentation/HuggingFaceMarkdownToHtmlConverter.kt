// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.documentation

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.pullrequest.comment.GHMarkdownToHtmlConverter


class HuggingFaceMarkdownToHtmlConverter(private val project: Project) {
  @NlsSafe
  @Nls
  fun convert(markdown: String): String {
    val convertedHtml = GHMarkdownToHtmlConverter(project).convertMarkdown(markdown)
    val htmlWithoutBaseTags = convertedHtml.replace(
      Regex("<(/)?(html|head|body)( [^>]*)?>"),
      ""
    )
    val htmlWithFixedPreTags = htmlWithoutBaseTags.replace(
      Regex("<pre>"),
      "<pre class=${HuggingFaceQuickDocStyles.HF_PRE_TAG_CLASS}>"
    )
    val htmlWithFixedPTags = htmlWithFixedPreTags.replace(
      Regex("<p>"),
      "<p class=${HuggingFaceQuickDocStyles.HF_P_TAG_CLASS}>"
    )
    val htmlWithFixedQuotes = addDivToBlockquotes(htmlWithFixedPTags)
    val htmlFixedTables = formatTablesInHtml(htmlWithFixedQuotes)
    return wrapPythonCodeInContainer(htmlFixedTables)
  }

  private fun addDivToBlockquotes(html: String): String {
    val blockquoteRegex = Regex("<blockquote>(.*?)</blockquote>", RegexOption.DOT_MATCHES_ALL)
    return html.replace(blockquoteRegex) {
      "<blockquote><div class=\"${HuggingFaceQuickDocStyles.QUOTE_CLASS}\">${it.groupValues[1]}</div></blockquote>"
    }
  }

  private fun formatTablesInHtml(html: String): String {
    val htmlNoIntellijRowEven = html.replace(" class=\"intellij-row-even\"", "")
    val tableRegex = Regex("<table.*?>(.*?)</table>", RegexOption.DOT_MATCHES_ALL)
    val tdRegex = Regex("<td>(.*?)</td>", RegexOption.DOT_MATCHES_ALL)

    return htmlNoIntellijRowEven.replace(tableRegex) { tableMatch ->
      val formattedTable = "<table class='sections'>${tableMatch.groupValues[1]}</table>"

      formattedTable.replace(tdRegex) { tdMatch ->
        "<td valign='top' class='section'>${tdMatch.groupValues[1]}</td>"
      }
    }
  }

  private fun wrapPythonCodeInContainer(html: String): String {
    val codePattern = Regex("<code class=\"[^\"]+\">(.*?)</code>", RegexOption.DOT_MATCHES_ALL)
    var modifiedHtml = html

    codePattern.findAll(html).forEach { matchResult ->
      val originalCodeHtml = matchResult.value
      val wrappedCodeHtml = "<div class=\"${HuggingFaceQuickDocStyles.CODE_DIV_CLASS}\">$originalCodeHtml</div>"
      modifiedHtml = modifiedHtml.replace(originalCodeHtml, wrappedCodeHtml)
    }
    return modifiedHtml
  }
}