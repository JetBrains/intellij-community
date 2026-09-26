// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.tests.reworked.util

import com.intellij.terminal.tests.reworked.util.TerminalOutputPattern.Companion.STYLES
import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.session.impl.Osc8Hyperlink
import org.jetbrains.plugins.terminal.session.impl.StyleRange
import org.jetbrains.plugins.terminal.view.TerminalLineIndex
import org.jetbrains.plugins.terminal.view.TerminalOffset
import org.jetbrains.plugins.terminal.view.impl.MutableTerminalOutputModel
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser

internal class TerminalOutputPattern(
  val text: String,
  val styles: List<StyleRange>,
  val cursorOffset: Int?,
  val osc8Hyperlinks: List<Osc8Hyperlink>,
) {
  override fun toString(): String {
    return asString()
  }

  /**
   * Consider patterns with same adjacent style ranges equal:
   * `<s1>hel</s1><s1>lo</s1>` == `<s1>hello</s1>`
   */
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is TerminalOutputPattern) return false
    return text == other.text &&
      cursorOffset == other.cursorOffset &&
      mergeAdjacentStyles(styles) == mergeAdjacentStyles(other.styles) &&
      osc8Hyperlinks == other.osc8Hyperlinks
  }

  override fun hashCode(): Int {
    var result = text.hashCode()
    result = 31 * result + mergeAdjacentStyles(styles).hashCode()
    result = 31 * result + (cursorOffset ?: 0)
    result = 31 * result + osc8Hyperlinks.hashCode()
    return result
  }

  companion object {
    val STYLES: List<TextStyle> = listOf(
      TextStyle(TerminalColor.index(1), null),
      TextStyle(TerminalColor.index(2), null),
      TextStyle(TerminalColor.index(3), null),
      TextStyle(TerminalColor.index(4), null),
      TextStyle(TerminalColor.index(5), null),
      TextStyle(TerminalColor.index(6), null),
      TextStyle(TerminalColor.index(7), null),
      TextStyle(TerminalColor.index(8), null),
      TextStyle(TerminalColor.index(9), null),
    )
  }
}

private val STYLE_TAG_REGEX = Regex("^s([1-9])$")
private const val LINK_TAG_NAME = "a"
private const val LINK_HREF_ATTR = "href"

/**
 * Define the content of the [org.jetbrains.plugins.terminal.view.TerminalOutputModel] using a string with XML markup:
 * 1. Use `<cursor>` tag inside string to specify cursor position
 * 2. Use `<s1>`, `<s2>`, etc. tags to specify styles, like `<s1>hello</s1>`
 * 3. Use `<a href="...">` tags to specify OSC8 hyperlinks, like `<a href="https://example.com">hello</a>`
 */
internal fun outputPattern(pattern: String): TerminalOutputPattern {
  // Preprocess: replace <cursor> with <cursor/> so JSoup XML parser treats it as self-closing
  val preprocessed = pattern.replace("<cursor>", "<cursor/>")
  val doc = Jsoup.parse(preprocessed, "", Parser.xmlParser())

  val textBuilder = StringBuilder()
  val styles = mutableListOf<StyleRange>()
  val osc8Hyperlinks = mutableListOf<Osc8Hyperlink>()
  var cursorOffset: Int? = null

  // currentTag is the tag we're nested inside, e.g. "s1" or "a"; styles and links may not nest into each other.
  fun processNodes(nodes: List<Node>, currentTag: String?) {
    for (node in nodes) {
      when (node) {
        is TextNode -> {
          textBuilder.append(node.wholeText)
        }
        is Element -> {
          val tagName = node.tagName()
          if (tagName == "cursor") {
            require(cursorOffset == null) { "Multiple <cursor> tags are not allowed" }
            require(node.childNodeSize() == 0) { "<cursor> tag must be empty" }
            cursorOffset = textBuilder.length
          }
          else if (tagName == LINK_TAG_NAME) {
            require(currentTag == null) { "Nested <$tagName> tags are not allowed: <$tagName> inside <$currentTag>" }
            require(node.hasAttr(LINK_HREF_ATTR)) { "<a> tag must have an href attribute" }
            val uri = node.attr(LINK_HREF_ATTR)

            val startOffset = textBuilder.length
            val cursorBefore = cursorOffset
            processNodes(node.childNodes(), tagName)
            val endOffset = textBuilder.length

            require(!textBuilder.substring(startOffset, endOffset).contains('\n')) {
              "<a> tags cannot span multiple lines"
            }

            if (cursorOffset != null && cursorOffset != cursorBefore) {
              require(cursorOffset != startOffset && cursorOffset != endOffset) {
                "<cursor> must not be placed at the boundary inside <a>; place it outside the tag instead"
              }
            }

            if (startOffset != endOffset) {
              osc8Hyperlinks.add(Osc8Hyperlink(startOffset.toLong(), endOffset.toLong(), uri))
            }
          }
          else {
            val match = STYLE_TAG_REGEX.matchEntire(tagName)
            requireNotNull(match) { "Unknown tag: <$tagName>" }
            require(currentTag == null) { "Nested style tags are not allowed: <$tagName> inside <$currentTag>" }

            val styleNum = match.groupValues[1].toInt()
            val styleIndex = styleNum - 1

            val startOffset = textBuilder.length
            val cursorBefore = cursorOffset
            processNodes(node.childNodes(), tagName)
            val endOffset = textBuilder.length

            // Cursor was placed inside this style tag — check it's not at the boundary
            if (cursorOffset != null && cursorOffset != cursorBefore) {
              require(cursorOffset != startOffset && cursorOffset != endOffset) {
                "<cursor> must not be placed at the boundary inside <s$styleNum>; place it outside the tag instead"
              }
            }

            if (startOffset != endOffset) {
              styles.add(StyleRange(
                startOffset = startOffset.toLong(),
                endOffset = endOffset.toLong(),
                style = STYLES[styleIndex],
                ignoreContrastAdjustment = false,
              ))
            }
          }
        }
      }
    }
  }

  processNodes(doc.childNodes(), null)

  return TerminalOutputPattern(
    text = textBuilder.toString(),
    styles = styles,
    cursorOffset = cursorOffset,
    osc8Hyperlinks = osc8Hyperlinks,
  )
}

internal fun TerminalOutputPattern.asString(): String {
  val sb = StringBuilder()
  var pos = 0

  // Collect all insertion points: style opens, style closes, cursor
  data class Insertion(val offset: Int, val priority: Int, val tag: String)

  val insertions = mutableListOf<Insertion>()
  for (style in styles) {
    val styleIndex = STYLES.indexOf(style.style)
    check(styleIndex >= 0) { "Unknown style: ${style.style}" }
    val tagNum = styleIndex + 1
    insertions.add(Insertion(style.startOffset.toInt(), 2, "<s$tagNum>"))
    insertions.add(Insertion(style.endOffset.toInt(), 0, "</s$tagNum>"))
  }
  for (link in osc8Hyperlinks) {
    insertions.add(Insertion(link.startOffset.toInt(), 2, "<a href=\"${link.uri}\">"))
    insertions.add(Insertion(link.endOffset.toInt(), 0, "</a>"))
  }
  if (cursorOffset != null) {
    insertions.add(Insertion(cursorOffset, 1, "<cursor>"))
  }
  insertions.sortWith(compareBy({ it.offset }, { it.priority }))

  for (insertion in insertions) {
    sb.append(text, pos, insertion.offset)
    sb.append(insertion.tag)
    pos = insertion.offset
  }
  sb.append(text, pos, text.length)
  return sb.toString()
}

private fun mergeAdjacentStyles(styles: List<StyleRange>): List<StyleRange> {
  if (styles.isEmpty()) return styles
  val result = mutableListOf(styles[0])
  for (i in 1 until styles.size) {
    val last = result.last()
    val next = styles[i]
    if (next.startOffset == last.endOffset && next.style == last.style && next.ignoreContrastAdjustment == last.ignoreContrastAdjustment) {
      result[result.lastIndex] = StyleRange(last.startOffset, next.endOffset, last.style, last.ignoreContrastAdjustment)
    }
    else {
      result.add(next)
    }
  }
  return result
}

internal fun MutableTerminalOutputModel.updateContent(absoluteLineIndex: Long, pattern: TerminalOutputPattern) {
  updateContent(absoluteLineIndex, pattern.text, pattern.styles, pattern.osc8Hyperlinks)
  if (pattern.cursorOffset != null) {
    val lineStartOffset = getStartOfLine(TerminalLineIndex.of(absoluteLineIndex))
    updateCursorPosition(lineStartOffset + pattern.cursorOffset.toLong())
  }
}

internal fun MutableTerminalOutputModel.replaceContent(
  offset: TerminalOffset,
  length: Int,
  pattern: TerminalOutputPattern,
) {
  require(pattern.osc8Hyperlinks.isEmpty()) {
    "replaceContent cannot add OSC8 hyperlinks; the model clears links over the replaced range"
  }
  replaceContent(offset, length, pattern.text, pattern.styles)
}

internal fun MutableTerminalOutputModel.toPattern(): TerminalOutputPattern {
  val state = dumpState()
  val relativeStyles = state.highlightings.map {
    it.copy(
      startOffset = it.startOffset - state.trimmedCharsCount,
      endOffset = it.endOffset - state.trimmedCharsCount,
    )
  }
  val relativeLinks = state.osc8Hyperlinks.map {
    it.copy(
      startOffset = it.startOffset - state.trimmedCharsCount,
      endOffset = it.endOffset - state.trimmedCharsCount,
    )
  }
  return TerminalOutputPattern(
    text = state.text,
    styles = relativeStyles,
    cursorOffset = state.cursorOffset,
    osc8Hyperlinks = relativeLinks,
  )
}

internal fun MutableTerminalOutputModel.matches(pattern: TerminalOutputPattern): Boolean {
  return toPattern() == pattern
}

internal fun MutableTerminalOutputModel.assertMatches(pattern: TerminalOutputPattern) {
  val actual = toPattern()
  assertThat(actual).isEqualTo(pattern)
}