package org.intellij.plugins.markdown.extensions.common.highlighter

import com.intellij.lang.Language
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
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
import org.intellij.plugins.markdown.extensions.jcef.MarkdownASTNode
import org.intellij.plugins.markdown.lang.psi.util.hasType
import org.intellij.plugins.markdown.lang.psi.util.textRange
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.intellij.plugins.markdown.ui.preview.html.DefaultCodeFenceGeneratingProvider
import org.intellij.plugins.markdown.ui.preview.html.children
import org.intellij.plugins.markdown.ui.preview.jcef.CodeFenceParsingService
import org.intellij.plugins.markdown.ui.preview.jcef.CodeFenceParsingServiceImpl
import java.awt.Color
import kotlin.math.max
import kotlin.math.min

class FenceHighlighting()

private val fenceLanguage = Regex("^lang(uage)=")
private val terminalDoubleNewline = Regex("""\n\n$""", RegexOption.DOT_MATCHES_ALL)
private var fallbackParsingService: CodeFenceParsingService? = null // Only needed for testing Markdown plugin as a dropped-in .jar file
private val LOG = logger<FenceHighlighting>()

private fun parseContent(project: Project?, language: Language, text: String, node: ASTNode,
                         collector: (String, IntRange, Color?, String?) -> Unit) {
  val file = LightVirtualFile("markdown_temp", text)
  val highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, file)
  var fenceParsing = fallbackParsingService
  val ecm = EditorColorsManager.getInstance()
  var colorScheme = ecm.globalScheme
  val settings = project?.let(MarkdownSettings::getInstance)

  // This code is only needed for testing Markdown plugin as a dropped-in .jar file.
  if (fenceParsing == null) {
    try { // Normally this should never fail
      fenceParsing = service<CodeFenceParsingService>()
    }
    catch (_: RuntimeException) {
      LOG.error("CodeFenceParsingService was not registered as a service. Creating a fallback instance instead.")
      try {
        fenceParsing = CodeFenceParsingServiceImpl()
        fallbackParsingService = fenceParsing
      }
      catch (_: Throwable) {
        LOG.error("Fallback CodeFenceParsingService failed to be created.")
      }
    }
  }

  if (settings?.style?.isVariable() != true) {
    val isCurrentThemeDark = ColorUtil.isDark(colorScheme.defaultBackground)
    val isMarkdownDark = settings!!.isDark()

    if (isCurrentThemeDark != isMarkdownDark) {
      colorScheme = ecm.getScheme(if (isMarkdownDark) "_@user_Dark" else "_@user_Light")
    }
  }

  if (settings.useAlternativeHighlighting && fenceParsing?.altHighlighterAvailable() == true) {
    val lang = (if (language == Language.ANY) ((node as? MarkdownASTNode)?.language ?: "") else language.id.lowercase())
        .replace(fenceLanguage, "")
    val html = fenceParsing.parseToHighlightedHtml(lang, text, node)?.replace(terminalDoubleNewline, "\n")

    if (html.isNullOrEmpty() && language == Language.ANY) {
      // Alterative highlighting failed, and default highlighting doesn't work for the current language.
      return
    }
    else if (html?.isNotEmpty() == true) {
      collector(text, 0..text.length, null, html)
      return
    }
    // Fall through to default highlighting if alternative failed but default understands the current language.
  }

  val psiFile = PsiFileFactory.getInstance(project).createFileFromText(file.name, language, text)

	// Traverse the PSI tree and collect highlights
	psiFile?.accept(object : PsiRecursiveElementVisitor() {
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

private inline fun collectHighlights(language: Language, text: String, project: Project? = null,
                                     node: ASTNode, crossinline consumer: (String, IntRange, Color?, String?) -> Unit) {
  parseContent(project, language, text, node) { content, range, color, html ->
    if (color != null || html != null) {
      consumer.invoke(content, range, color, html)
    }
  }
}

internal data class HighlightedRange(
  val range: TextRange,
  val color: Color? = null,
  val html: String? = null
): Comparable<HighlightedRange> {
  override fun compareTo(other: HighlightedRange): Int {
    return TextRangeUtil.RANGE_COMPARATOR.compare(range, other.range)
  }
}

internal fun collectHighlightedChunks(language: Language, text: String, project: Project?, node: ASTNode): List<HighlightedRange> {
  return buildList {
    collectHighlights(language, text, project, node) { _, range, color, id ->
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
    for ((range, color) in ranges) {
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
          val span = createSpan(range.shiftRight(contentOffset), range.substring(text), color)
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

private fun createSpan(range: TextRange, text: String = "", color: Color? = null): HtmlChunk {
  val span = HtmlChunk.span().sourceRange(range)
  if (text.isEmpty() && color == null) {
    return span
  }
  return span.escapedText(text).color(color)
}

private fun StringBuilder.appendChunk(chunk: HtmlChunk): StringBuilder {
  chunk.appendTo(this)
  return this
}

private fun HtmlChunk.Element.sourceRange(range: TextRange): HtmlChunk.Element {
  return attr(HtmlGenerator.SRC_ATTRIBUTE_NAME, "${range.startOffset}..${range.endOffset}")
}

private fun HtmlChunk.Element.escapedText(text: String): HtmlChunk.Element {
  @Suppress("HardCodedStringLiteral")
  return addRaw(DefaultCodeFenceGeneratingProvider.escape(text))
}

private fun HtmlChunk.Element.color(color: Color?): HtmlChunk.Element {
  if (color == null) {
    return this
  }
  val value = ColorUtil.toHtmlColor(color)
  return style("color: $value;")
}
