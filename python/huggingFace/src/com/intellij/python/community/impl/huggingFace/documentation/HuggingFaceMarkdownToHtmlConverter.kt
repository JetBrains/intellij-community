// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.documentation

import com.intellij.json.JsonLanguage
import com.intellij.lang.documentation.DocumentationMarkup.CLASS_SECTION
import com.intellij.lang.documentation.DocumentationMarkup.CLASS_SECTIONS
import com.intellij.markdown.utils.convertMarkdownToHtml
import com.intellij.markdown.utils.lang.HtmlSyntaxHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.python.PythonLanguage
import org.jetbrains.annotations.Nls


/**
 * Justification for the complexity of the HuggingFaceMarkdownToHtmlConverter class:
 * it is driven by the unique rendering needs within the quickDoc display context.
 * Specifically, pre-tags were enhanced with a subclass to ensure they adapt to the quickDoc window width.
 * Similarly, processing for blockquotes and tables was customized to  respect the display area's constraints.
 * These adjustments guarantee that the converted HTML content seamlessly fits within the quickDoc environment,
 * enhancing readability and user interaction.
 */
class HuggingFaceMarkdownToHtmlConverter(private val project: Project) {
  @NlsSafe
  @Nls
  fun convert(markdown: String): String {
    val convertedHtml = convertMarkdownToHtml(markdown)
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
    val fixedCodeBlocks = wrapCodeBlocks(htmlFixedTables, project)
    return fixedCodeBlocks
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
      val formattedTable = "<table class='$CLASS_SECTIONS'>${tableMatch.groupValues[1]}</table>"

      formattedTable.replace(tdRegex) { tdMatch ->
        "<td valign='top' class='$CLASS_SECTION'>${tdMatch.groupValues[1]}</td>"
      }
    }
  }

  private fun wrapCodeBlocks(html: String, project: Project?): String {
    val codePattern = Regex("(?s)<pre class=word-break-pre-class><code([^>]*)>(.*?)</code></pre>")
    var modifiedHtml = html

    codePattern.findAll(html).forEach { matchResult ->
      val originalCodeHtml = matchResult.value
      val codeAttributes = matchResult.groups[1]?.value ?: ""
      var rawCode = matchResult.groups[2]?.value ?: ""
      rawCode = decodeHtmlEntities(rawCode)

      val language = when {
        // PY-70540 - if additional languages are needed - to be added here
        "language-python" in codeAttributes -> PythonLanguage.INSTANCE
        "language-json" in codeAttributes -> JsonLanguage.INSTANCE
        else -> null
      }

      val highlightedHtmlChunk = if (language != null) {
        HtmlSyntaxHighlighter.colorHtmlChunk(project, language, rawCode).toString()
      } else {
        StringUtil.escapeXmlEntities(rawCode)
      }

      val wrappedCodeHtml = """
          <div class="${HuggingFaceQuickDocStyles.CODE_DIV_CLASS}">
            <code$codeAttributes>
              <pre class="${HuggingFaceQuickDocStyles.HF_PRE_TAG_CLASS}">$highlightedHtmlChunk</pre>
            </code>
          </div>
        """.trimIndent()
      modifiedHtml = modifiedHtml.replace(originalCodeHtml, wrappedCodeHtml)
    }

    return modifiedHtml
  }

  private fun decodeHtmlEntities(text: String): String {
    return text
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace("&quot;", "\"")
      .replace("&apos;", "'")
      .replace("&amp;", "&")
  }
}