package com.intellij.python.community.impl.huggingFace.documentation

import com.intellij.python.community.impl.huggingFace.HuggingFaceEntityKind
import com.intellij.python.community.impl.huggingFace.api.HuggingFaceURLProvider
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.util.Locale

@ApiStatus.Internal
class HuggingFaceReadmeCleaner(
  private var markdown: String,
  private val entityId: String,
  private val entityKind: HuggingFaceEntityKind,
  private val renderImages: Boolean = false,
) {
  private val cardUrl: URL = HuggingFaceURLProvider.getEntityCardLink(entityId, entityKind)

  fun doCleanUp(): HuggingFaceReadmeCleaner {
    // todo: some optimisation is needed:
    // headers are collected twice - in the increaseHeaderLevels and fixContentTables
    removeMetaData()
    increaseHeaderLevels()
    fixCodeFences()
    cleanupNotSupportedElements()
    if (renderImages) {
      normalizeImageContainers()
      convertRelativeImageLinksToAbsolute()
    }
    else {
      cleanupImages()
    }
    convertRelativeFileLinksToAbsolute()
    fixContentTables()
    removeMarkdownSeparators()
    return this
  }

  private fun removeMetaData() {
    // README.md files in HF repos have a header with metadata, which we are not going to use here
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
    val internalLinks = INTERNAL_LINK_REGEX.findAll(markdown).map { it.groupValues[2] }.toList()
    val headers = MARKDOWN_HEADER_REGEX.findAll(markdown).map { it.value.trim() }.toList()

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
      .replace(SUMMARY_TAGS_REGEX) { matchResult ->
        matchResult.groupValues[1]
      }
  }

  private fun normalizeImageContainers() {
    markdown = CENTER_BLOCK_REGEX.replace(markdown) { matchResult ->
      val content = matchResult.groupValues[1]
      if (HTML_IMG_REGEX.containsMatchIn(content)) createCenteredParagraph(content) else matchResult.value
    }
    markdown = CENTER_ALIGNED_DIV_BLOCK_REGEX.replace(markdown) { matchResult ->
      val content = matchResult.groupValues[1]
      if (HTML_IMG_REGEX.containsMatchIn(content)) createCenteredParagraph(content) else matchResult.value
    }
  }

  private fun createCenteredParagraph(content: String): String {
    val flattenedContent = HTML_PARAGRAPH_OPENING_TAG_REGEX.replace(content.trimIndent(), "<br>")
      .let { HTML_PARAGRAPH_CLOSING_TAG_REGEX.replace(it, "") }
    return "\n<p align=\"center\">\n$flattenedContent\n</p>\n"
  }

  private fun cleanupImages() {
    // See PY-70539 -> potentially we could keep svgs
    val markdownImgPattern = MD_IMG_REGEX
    markdown = markdownImgPattern.replace(markdown) { matchResult ->
      val altText = matchResult.groupValues[1].ifBlank { matchResult.groupValues[2].split("/").last() }
      "\n[Image: $altText]($cardUrl)\n"
    }

    val htmlImgPattern = HTML_IMG_REGEX
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

  private fun convertRelativeImageLinksToAbsolute() {
    markdown = MD_IMG_REGEX.replace(markdown) { matchResult ->
      val imageUrl = matchResult.groupValues[2]
      if (isAbsoluteOrInternalUrl(imageUrl)) {
        matchResult.value
      }
      else {
        val absoluteUrl = HuggingFaceURLProvider.makeAbsoluteFileDownloadLink(entityId, entityKind, imageUrl)
        "![${matchResult.groupValues[1]}]($absoluteUrl)"
      }
    }

    markdown = HTML_IMG_REGEX.replace(markdown) { matchResult ->
      val imageTag = matchResult.value
      val srcMatch = HTML_IMG_SRC_REGEX.find(imageTag) ?: return@replace imageTag
      val srcGroup = srcMatch.groups.asSequence().drop(1).filterNotNull().firstOrNull() ?: return@replace imageTag
      val imageSource = makeRenderableImageUrl(srcGroup.value)
      val imageTagWithRenderableSource = imageTag.replaceRange(srcGroup.range, imageSource)
      normalizeImageSize(imageTagWithRenderableSource, imageSource)
    }
  }

  private fun makeRenderableImageUrl(imageUrl: String): String {
    val absoluteUrl = if (isAbsoluteOrInternalUrl(imageUrl)) {
      imageUrl
    }
    else {
      HuggingFaceURLProvider.makeAbsoluteFileDownloadLink(entityId, entityKind, imageUrl).toString()
    }
    return try {
      URI(absoluteUrl).toASCIIString()
    }
    catch (_: URISyntaxException) {
      absoluteUrl
    }
  }

  private fun removeImageHeight(imageTag: String): String {
    val imageTagWithoutHeight = HTML_IMG_HEIGHT_ATTRIBUTE_REGEX.replace(imageTag, "")
    return HTML_STYLE_ATTRIBUTE_REGEX.replace(imageTagWithoutHeight) { matchResult ->
      val quote = matchResult.groupValues[1]
      val declarations = matchResult.groupValues[2]
        .split(';')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .filterNot { it.substringBefore(':').trim().equals("height", ignoreCase = true) }
      if (declarations.isEmpty()) "" else "style=$quote${declarations.joinToString("; ")}$quote"
    }
  }

  private fun normalizeImageSize(imageTag: String, imageSource: String): String {
    val isBadge = BADGE_IMAGE_URL_REGEX.containsMatchIn(imageSource)
    if (!isBadge && !HTML_IMG_WIDTH_ATTRIBUTE_REGEX.containsMatchIn(imageTag)) return imageTag

    val imageTagWithoutHeight = removeImageHeight(imageTag)
    if (!isBadge) return imageTagWithoutHeight

    val closingTag = if (imageTagWithoutHeight.endsWith("/>")) "/>" else ">"
    return "${imageTagWithoutHeight.removeSuffix(closingTag).trimEnd()} height=\"$BADGE_HEIGHT\"$closingTag"
  }

  private fun isAbsoluteOrInternalUrl(url: String): Boolean = ABSOLUTE_OR_INTERNAL_URL_REGEX.containsMatchIn(url)

  private fun convertRelativeFileLinksToAbsolute() {
    // Catch relative links to files excluding internal markdown links (like in tables of content)
    val regex = RELATIVE_LINK_REGEX
    markdown = regex.replace(markdown) { matchResult ->
      val (linkText, relativePath) = matchResult.destructured
      val absoluteUrl = HuggingFaceURLProvider.makeAbsoluteFileLink(entityId, relativePath).toString()
      "[$linkText]($absoluteUrl)"
    }
  }

  private fun removeMarkdownSeparators() {
    markdown = markdown.replace(MD_SEPARATORS_REGEX, "\n")
  }

  @Nls
  fun getMarkdown(): String {
    return markdown.ifEmpty {
      HuggingFaceDocumentationPlaceholdersUtil.noReadmePlaceholder(entityId, entityKind)
    }
  }

  companion object {
    private const val HF_MD_HEADER_SEPARATOR = "---\n"
    private const val ERR_PY_CODE_FENCE_HEADER = "```py\n"
    private const val PY_CODE_FENCE_HEADER = "```python\n"
    private const val BADGE_HEIGHT = 20

    private val MD_IMG_REGEX = Regex("""!\[(.*?)]\((.*?)\)""")
    private val HTML_IMG_REGEX = Regex("""<img([^>]+)?>""", RegexOption.IGNORE_CASE)
    private val HTML_IMG_SRC_REGEX = Regex("""\bsrc\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)
    private val HTML_IMG_HEIGHT_ATTRIBUTE_REGEX = Regex(
      """\s+height\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)""",
      RegexOption.IGNORE_CASE,
    )
    private val HTML_IMG_WIDTH_ATTRIBUTE_REGEX = Regex(
      """\s+width\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)""",
      RegexOption.IGNORE_CASE,
    )
    private val HTML_STYLE_ATTRIBUTE_REGEX = Regex(
      """\bstyle\s*=\s*(["'])(.*?)\1""",
      setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    private val ABSOLUTE_OR_INTERNAL_URL_REGEX = Regex("""^(?:[a-z][a-z0-9+.-]*:|//|#)""", RegexOption.IGNORE_CASE)
    private val CENTER_BLOCK_REGEX = Regex(
      """<center\b[^>]*>(.*?)</center\s*>""",
      setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    private val CENTER_ALIGNED_DIV_BLOCK_REGEX = Regex(
      """<div\b(?=[^>]*\balign\s*=\s*(?:"center"|'center'|center))[^>]*>(.*?)</div\s*>""",
      setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    private val HTML_PARAGRAPH_OPENING_TAG_REGEX = Regex("""<p\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val HTML_PARAGRAPH_CLOSING_TAG_REGEX = Regex("""</p\s*>""", RegexOption.IGNORE_CASE)
    private val BADGE_IMAGE_URL_REGEX = Regex(
      """img\.shields\.io/|/badge(?:[-_.][^/?#]*)?\.svg(?:[?#]|$)""",
      RegexOption.IGNORE_CASE,
    )
    private val MARKDOWN_HEADER_REGEX = Regex("""(?m)^#{1,6}\s(.*?)$""")
    private val INTERNAL_LINK_REGEX = Regex("""\[(.*?)\]\(#(.*?)\)""")
    private val RELATIVE_LINK_REGEX = Regex("""\[(.*?)\]\((?!http|#)(.*?)(?<!\.(jpg|jpeg|png|gif))\)""")
    private val SUMMARY_TAGS_REGEX = Regex("<summary>(.*?)</summary>")
    private val MD_SEPARATORS_REGEX = Regex("\\n(-{3,}|_{3,}|\\*{3,})\\n")
  }
}
