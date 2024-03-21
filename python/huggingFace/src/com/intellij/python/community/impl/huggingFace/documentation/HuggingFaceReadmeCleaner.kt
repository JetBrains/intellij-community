package com.intellij.python.community.impl.huggingFace.documentation

import com.intellij.python.community.impl.huggingFace.HuggingFaceEntityKind
import com.intellij.python.community.impl.huggingFace.api.HuggingFaceURLProvider
import org.jetbrains.annotations.ApiStatus
import java.net.URL
import java.util.*

@ApiStatus.Internal
class HuggingFaceReadmeCleaner(
  private var markdown: String,
  private val entityId: String,
  private val entityKind: HuggingFaceEntityKind
) {
  private val cardUrl: URL = HuggingFaceURLProvider.getEntityCardLink(entityId, entityKind)

  fun doCleanUp(): HuggingFaceReadmeCleaner {
    // todo: some optimisation is needed:
    // headers are collected twice - in the increaseHeaderLevels and fixContentTables
    removeMetaData()
    increaseHeaderLevels()
    fixCodeFences()
    cleanupNotSupportedElements()
    cleanupImages()
    convertRelativeFileLinksToAbsolute()
    fixContentTables()
    processMarkdownTables()
    removeMarkdownSeparators()
    // trimLongMd()
    return this
  }

  private fun removeMetaData() {
    val parts = markdown.split(HF_MD_HEADER_SEPARATOR)
    markdown = if (parts.size > 2) {
      parts.drop(2).joinToString(HF_MD_HEADER_SEPARATOR)
    } else {
      markdown
    }
  }

  private fun increaseHeaderLevels() {
    val pattern = """(?m)^#{1,5}\s""".toRegex()
    markdown = pattern.replace(markdown) { matchResult ->
      "#${matchResult.value}"
    }
  }

  private fun fixContentTables() {
    val internalLinksRegex = INTERNAL_LINK_PATTERN.toRegex()
    val headersRegex = MARKDOWN_HEADER_PATTERN.toRegex()

    val internalLinks = internalLinksRegex.findAll(markdown).map { it.groupValues[2] }.toList()
    val headers = headersRegex.findAll(markdown).map { it.value.trim() }.toList()

    internalLinks.forEach { link ->
      val anchor = "<a name=\"$link\"></a>"
      if (markdown.contains(anchor)) {
        return@forEach
      }

      val normalizedLink = link.replace("-", "").lowercase(Locale.getDefault())

      headers.forEach { header ->
        val normalizedHeader = header
                .replace(Regex("^#{1,6}\\s"), "")
                .replace(" ", "").lowercase(Locale.getDefault())
        if (normalizedLink == normalizedHeader) {
          // Find the position of the header and insert the anchor above it
          val headerIndex = markdown.indexOf(header)
          if (headerIndex != -1) {
            markdown = markdown.substring(0, headerIndex) + "$anchor\n" + markdown.substring(headerIndex)
          }
        }
      }
    }
  }

  private fun fixCodeFences() {
    markdown = markdown.replace(ERR_PY_CODE_FENCE_HEADER, PY_CODE_FENCE_HEADER)
  }

  private fun cleanupNotSupportedElements() {
    markdown = markdown
      .replace("<details>", "")
      .replace("</details>", "")
      .replace(Regex(SUMMARY_TAGS_PATTERN)) { matchResult ->
        matchResult.groupValues[1] // Return only the content captured between <summary> tags
      }
  }

  private fun cleanupImages() {
    // See PY-70539 -> potentially we could keep svgs
    val markdownImgPattern = Regex(MD_IMG_PATTERN)
    markdown = markdownImgPattern.replace(markdown) { matchResult ->
      val altText = matchResult.groupValues[1].ifBlank { matchResult.groupValues[2].split("/").last() }
      "\n[Image: $altText]($cardUrl)\n"
    }

    val htmlImgPattern = Regex(HTML_IMG_PATTERN, RegexOption.IGNORE_CASE)
    markdown = htmlImgPattern.replace(markdown) { matchResult ->
      val imgTag = matchResult.value
      val altPattern = Regex("""\balt=(['"]?)(.*?)\1""", RegexOption.IGNORE_CASE)

      val altText = altPattern.find(imgTag)?.groupValues?.get(2)
      val srcPattern = Regex("""\bsrc=(['"]?)(.*?)\1""", RegexOption.IGNORE_CASE)
      val srcValue = srcPattern.find(imgTag)?.groupValues?.get(2)
      val filename = srcValue?.split("/")?.lastOrNull()

      "\n[Image: ${altText?: filename}]($cardUrl)\n"
    }
  }

  private fun convertRelativeFileLinksToAbsolute() {
    // Catch relative links to files excluding internal markdown links (like in tables of content)
    val regex = RELATIVE_LINK_PATTERN.toRegex()
    markdown = regex.replace(markdown) { matchResult ->
      val (linkText, relativePath) = matchResult.destructured
      val absoluteUrl = HuggingFaceURLProvider.makeAbsoluteFileLink(entityId, relativePath).toString()
      "[$linkText]($absoluteUrl)"
    }
  }

  private fun processMarkdownTables() {
    val lines = markdown.split("\n")
    val processedLines = mutableListOf<String>()
    var isTable = false
    var table = mutableListOf<String>()

    for (line in lines) {
      if (line.startsWith("|") && line.endsWith("|")) {
        isTable = true
        table.add(line)
      } else {
        if (isTable) {
          processedLines.addAll(truncateTable(table))
          table = mutableListOf()
          isTable = false
        }
        processedLines.add(line)
      }
    }

    if (isTable) {
      processedLines.addAll(truncateTable(table))
    }
    markdown = processedLines.joinToString("\n")
  }

  private fun truncateTable(table: MutableList<String>): List<String> {
    val header = table.first()
    val columnCount = header.split("|").filter { it.isNotBlank() }.size

    if (columnCount <= 4) return table

    val truncatedTable = mutableListOf<String>()
    truncatedTable.add(header.split("|").take(4).joinToString("|") + "|...|")
    val separator = table[1].split("|").take(4).joinToString("|") + "|---|"
    truncatedTable.add(separator)
    for (row in table.drop(2)) {
      val cells = row.split("|")
      if (cells.all { it.isBlank() }) { continue }
      val modifiedRow = cells.take(4).joinToString("|") + "|...|"
      truncatedTable.add(modifiedRow)
    }

    return truncatedTable
  }

  private fun removeMarkdownSeparators() {
    markdown = markdown
      .replace(Regex("\\n[-]{3,}\\n"), "\n")
      .replace(Regex("\\n[*]{3,}\\n"), "\n")
      .replace(Regex("\\n[_]{3,}\\n"), "\n")
  }

  fun getMarkdown(): String {
    return markdown.ifEmpty {
      HuggingFaceDocumentationPlaceholdersUtil.noReadmePlaceholder(entityId, entityKind)
    }
  }

  companion object {
    private const val HF_MD_HEADER_SEPARATOR = "---\n"
    private const val ERR_PY_CODE_FENCE_HEADER = "```py\n"
    private const val PY_CODE_FENCE_HEADER = "```python\n"
    private const val CODE_FENCE_MARKER = "```"
    private const val MD_IMG_PATTERN = """!\[(.*?)]\((.*?)\)"""
    private const val HTML_IMG_PATTERN = """<img([^>]+)?>"""
    private const val MARKDOWN_HEADER_PATTERN = """(?m)^#{1,6}\s(.*?)$"""
    private const val INTERNAL_LINK_PATTERN = """\[(.*?)\]\(#(.*?)\)"""
    private const val RELATIVE_LINK_PATTERN = """\[(.*?)\]\((?!http|#)(.*?)(?<!\.(jpg|jpeg|png|gif))\)"""
    private const val SUMMARY_TAGS_PATTERN = "<summary>(.*?)</summary>"
  }
}