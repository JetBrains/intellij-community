package org.intellij.plugins.markdown.extensions.common.highlighter

import com.intellij.lang.Language
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.psi.*
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.ColorUtil
import com.intellij.util.text.TextRangeUtil
import com.intellij.util.text.splitToTextRanges
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.plugins.markdown.lang.psi.util.hasType
import org.intellij.plugins.markdown.lang.psi.util.textRange
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.intellij.plugins.markdown.ui.preview.html.DefaultCodeFenceGeneratingProvider
import org.intellij.plugins.markdown.ui.preview.html.children
import java.awt.Color
import kotlin.math.max
import kotlin.math.min

var codeFenceId: Long = 0

private fun parseContent(project: Project?, language: Language, text: String, languageName: String?,
                         collector: (String, IntRange, Color?, Long?) -> Unit) {
  val file = LightVirtualFile("markdown_temp", text)
  val highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, file)
  val ecm = EditorColorsManager.getInstance()
  var colorScheme = ecm.globalScheme
  val settings = project?.let(MarkdownSettings::getInstance)

  if (settings?.style?.isVariable() != true) {
    val isCurrentThemeDark = ColorUtil.isDark(colorScheme.defaultBackground)
    val isMarkdownDark = settings!!.isDark()

    if (isCurrentThemeDark != isMarkdownDark) {
      colorScheme = ecm.getScheme(if (isMarkdownDark) "_@user_Dark" else "_@user_Light")
    }
  }

  if (settings.useAlternativeHighlighting) {
    // val lang = if (language == Language.ANY) (languageName ?: "") else language.id.lowercase()
    val lines = text.split(Regex("\r\n|\r|\n")).map { it + '\n' }
    var offset = 0

    if (lines.lastOrNull()?.trim()?.isEmpty() == true) {
      lines.dropLast(1)
    }

    ++codeFenceId

    for (i in lines.indices) {
      val line = lines[i]

      collector(line, offset..offset + line.length, null, if (i == 0) codeFenceId else null)
      offset += line.length
    }

    return
  }

  val psiFile = PsiFileFactory.getInstance(project).createFileFromText(file.name, language, text)

	// Traverse the PSI tree and collect highlights
	psiFile.accept(object : PsiRecursiveElementVisitor() {
		override fun visitElement(element: PsiElement) {
			super.visitElement(element)
			val range = element.textRange
      val attributesKey = highlighter.getTokenHighlights(element.node.elementType).lastOrNull()
			val color = colorScheme.getAttributes(attributesKey)?.foregroundColor
            ?: colorScheme.defaultForeground

			collector(element.text, range.startOffset..range.endOffset, color, null)
		}
	})
}

private inline fun collectHighlights(language: Language, text: String, project: Project? = null, languageName: String? = null,
                                     crossinline consumer: (String, IntRange, Color?, Long?) -> Unit) {
  parseContent(project, language, text, languageName) { content, range, color, id ->
    if (color != null || id != null) {
      consumer.invoke(content, range, color, id)
    }
  }
}

internal data class HighlightedRange(
  val range: TextRange,
  val color: Color? = null,
  val id: Long? = null,
): Comparable<HighlightedRange> {
  override fun compareTo(other: HighlightedRange): Int {
    return TextRangeUtil.RANGE_COMPARATOR.compare(range, other.range)
  }
}

internal fun collectHighlightedChunks(language: Language, text: String, project: Project?, languageName: String): List<HighlightedRange> {
  return buildList {
    collectHighlights(language, text, project, languageName) { _, range, color, id ->
      add(HighlightedRange(TextRange(range.first, range.last), color, id))
    }
  }
}

internal fun buildHighlightedFenceContent(
  text: String,
  highlightedRanges: List<HighlightedRange>,
  node: ASTNode,
  useAbsoluteOffsets: Boolean,
  additionalLineProcessor: ((String) -> String)? = null
): String {
  val contentBaseOffset = DefaultCodeFenceGeneratingProvider.calculateCodeFenceContentBaseOffset(node)
  check(contentBaseOffset >= node.startOffset) { "Content base offset should not be before fence start" }
  val builder = StringBuilder()
  val fenceStartOffset = when {
    useAbsoluteOffsets -> node.startOffset
    else -> 0
  }
  val contentOffset = when {
    useAbsoluteOffsets -> contentBaseOffset
    else -> contentBaseOffset - node.startOffset
  }
  check(contentOffset >= fenceStartOffset) { "Content offset should not be before fence start" }
  builder.appendChunk(createSpan(TextRange(fenceStartOffset, contentOffset)))
  processLines(builder, highlightedRanges, text, contentOffset, additionalLineProcessor)
  val closingDelimiter = node.children().firstOrNull { it.hasType(MarkdownTokenTypes.CODE_FENCE_END) }
  if (closingDelimiter != null) {
    val range = closingDelimiter.textRange
    val actualRange = when {
      useAbsoluteOffsets -> range
      else -> range.shiftLeft(node.startOffset)
    }
    builder.appendChunk(createSpan(actualRange))
  }
  return builder.toString()
}

@Suppress("UsePropertyAccessSyntax", "NAME_SHADOWING")
private fun processLines(
  builder: StringBuilder,
  highlightedRanges: List<HighlightedRange>,
  text: String,
  contentOffset: Int,
  additionalLineProcessor: ((String) -> String)? = null
) {
  for (lineRange in text.lineRanges(includeDelimiter = true)) {
    val ranges = highlightedRanges.filter { (range, _) -> lineRange.intersects(range) }

    if (ranges.isEmpty()) {
      val span = createSpan(lineRange.shiftRight(contentOffset), lineRange.substring(text))
      builder.appendChunk(span)
      continue
    }

    var left = lineRange.startOffset

    for ((range, color, id) in ranges) {
      val start = max(left, range.startOffset)
      val end = min(lineRange.endOffset, range.endOffset)

      if (start != left) {
        val range = TextRange(left, start)
        val span = createSpan(range.shiftRight(contentOffset), range.substring(text))
        builder.appendChunk(span)
      }

      if (end - start > 0) {
        val range = TextRange(start, end)
        if (!range.isEmpty()) {
          val span = createSpan(range.shiftRight(contentOffset), range.substring(text), color, id)
          builder.appendChunk(span)
        }
      }

      left = end
    }

    if (left != lineRange.endOffset) {
      val range = TextRange(left, lineRange.endOffset)
      val span = createSpan(range.shiftRight(contentOffset), range.substring(text))
      builder.appendChunk(span)
    }

    if (additionalLineProcessor != null) {
      val processed = additionalLineProcessor.invoke(lineRange.substring(text))
      builder.append(processed)
    }
  }
}

private fun CharSequence.lineRanges(includeDelimiter: Boolean = true): Sequence<TextRange> {
  return splitToTextRanges(this, delimiter = "\n", includeDelimiter = includeDelimiter)
}

private val replacements: Map<Char, String> = mapOf('&' to "&amp;", '>' to "&gt;", '<' to "&lt;", '"' to "&quot;")

private fun encodeEntities(text: String): String {
  return text.replace(Regex("""([&><"])""")) { match -> replacements[match.groupValues[1][0]].orEmpty() }
}

private fun createSpan(range: TextRange, text: String = "", color: Color? = null, id: Long? = null): HtmlChunk {
  var span = HtmlChunk.span().sourceRange(range)

  if (text.isEmpty() && color == null && id == null) {
    return span
  }

  span = span.addRaw(encodeEntities(text)).color(color)

  if (id != null) {
    span = span.attr("id", "cfid-$id").setClass("cfid")
  }

  return span
}

private fun StringBuilder.appendChunk(chunk: HtmlChunk): StringBuilder {
  chunk.appendTo(this)
  return this
}

private fun HtmlChunk.Element.sourceRange(range: TextRange): HtmlChunk.Element {
  return attr(HtmlGenerator.SRC_ATTRIBUTE_NAME, "${range.startOffset}..${range.endOffset}")
}

private fun HtmlChunk.Element.color(color: Color?): HtmlChunk.Element {
  if (color == null) {
    return this
  }
  val value = ColorUtil.toHtmlColor(color)
  return style("color: $value;")
}
