// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.testAssertions

import com.intellij.python.testAssertions.PyTestAssertionSyntax.COMMENT_CHAR
import com.intellij.python.testAssertions.PyTestAssertionSyntax.GUIDE_BAR
import com.intellij.python.testAssertions.PyTestAssertionSyntax.MARKER_CORNER
import com.intellij.python.testAssertions.PyTestAssertionSyntax.MARKER_LEFT
import com.intellij.python.testAssertions.PyTestAssertionSyntax.MARKER_SPAN

/**
 * One assertion of a test text: an expected one from the parser, or an actual one from the engine.
 *
 * The code position is the checked element. The assertion position is the comment that holds the assertion.
 * A column of -1 means "not set". An inline assertion has [codeColumnStart] -1 and applies to the whole line.
 */
data class PyTestAssertion(
  val codeOffsetStart: Int,
  val codeLineStart: Int,
  val codeColumnStart: Int = -1,
  val codeColumnEnd: Int = -1,
  val assertionOffsetStart: Int = -1,
  val assertionOffsetEnd: Int = -1,
  val assertionLineStart: Int = -1,
  val assertionLineEnd: Int = -1,
  val assertionColumnStart: Int = -1,
  val assertionColumnEnd: Int = -1,
  /** The name of a [PyTestDirective], or an unknown name from a wrong test text. */
  val type: String,
  val content: String,
  val fixmeContent: String? = null,
  val comment: String? = null,
) {

  val directive: PyTestDirective? get() = PyTestDirectives.find(type)

  val isSeverity: Boolean get() = type in PyTestDirectives.severityNames

  fun withContent(
    content: String = this.content,
    fixmeContent: String? = this.fixmeContent,
    comment: String? = this.comment,
  ): PyTestAssertion {

    return copy(content = content, fixmeContent = fixmeContent, comment = comment)
  }

  fun asInlineAssertion(): PyTestAssertion {
    return copy(codeColumnStart = -1, codeColumnEnd = -1)
  }

  fun isInlineAssertion(): Boolean {
    return codeColumnStart < 0
  }

  val isWildcard: Boolean get() = type == PyTestDirectives.ISSUES.name

  val codeColumnEndEffective: Int get() = if (codeColumnStart + 1 == codeColumnEnd) -1 else codeColumnEnd

  val columnLength: Int get() = (codeColumnEnd - codeColumnStart).coerceAtLeast(0)

  val hasFixme: Boolean get() = fixmeContent != null

  override fun toString(): String {
    val line = if (assertionLineStart > -1) assertionLineStart else codeLineStart
    val fixme = fixmeContent?.let { "${PyTestDirectives.FIXME.name} $it" } ?: ""
    val comment = comment?.takeIf { it.isNotEmpty() }?.let { " # $it" } ?: ""
    return "[$line:$codeColumnStart] $type $content $fixme$comment"
  }

  fun asText(serializedAssertionsAbove: List<String> = emptyList()): Pair<String, List<String>> {
    val normalizedCodeColumnStart = if (codeColumnStart == 0) 1 else codeColumnStart

    val serializedAssertionsAboveCopy = serializedAssertionsAbove.toMutableList()
    if (normalizedCodeColumnStart > 0) {
      for (i in serializedAssertionsAbove.indices) {
        serializedAssertionsAboveCopy[i] = insertGuideAtColumn(serializedAssertionsAbove[i], normalizedCodeColumnStart)
      }
    }

    return buildAssertionText(normalizedCodeColumnStart) to serializedAssertionsAboveCopy
  }

  private fun insertGuideAtColumn(text: String, column: Int): String {
    val chars = text.toCharArray()
    if (column < 0 || column >= chars.size) {
      return text
    }
    if (chars[column] == ' ' || (
        chars[column - 1] == MARKER_SPAN
        && chars[column] == MARKER_SPAN
        && chars[column + 1] == MARKER_SPAN
                                )
    ) {
      chars[column] = GUIDE_BAR
    }
    return String(chars)
  }

  private fun buildAssertionText(normalizedCodeColumnStart: Int): String {
    val suffix = buildSuffix()

    if (normalizedCodeColumnStart < 0) {
      return "# $type$suffix"
    }

    val prefix = buildMarkerPrefix(normalizedCodeColumnStart, codeColumnEndEffective)
    return "#$prefix $type$suffix"
  }

  private fun buildSuffix(): String {
    val result = StringBuilder()

    if (content.isNotBlank()) {
      result.append(" ")
      result.append(content)
    }

    if (fixmeContent != null) {
      result.append(" ")
      result.append(PyTestDirectives.FIXME.name)
      if (fixmeContent.isNotBlank()) {
        result.append(" ")
        result.append(fixmeContent)
      }
    }

    if (comment?.isNotBlank() == true) {
      result.append(" ")
      result.append(COMMENT_CHAR)
      result.append(" ")
      result.append(comment)
    }

    return result.toString()
  }

  private fun buildMarkerPrefix(normalizedCodeColumnStart: Int, codeColumnEnd: Int): String {
    val prefix = StringBuilder()

    repeat(normalizedCodeColumnStart - 1) {
      prefix.append(' ')
    }

    val spanLength = if (codeColumnEnd > 0) (codeColumnEnd - normalizedCodeColumnStart).coerceAtLeast(1) else 1
    when (spanLength) {
      1 if (codeColumnStart == 0) -> prefix.append(MARKER_LEFT)
      1 -> prefix.append(MARKER_CORNER)
      else -> {
        repeat(spanLength) {
          prefix.append(MARKER_SPAN)
        }
      }
    }

    return prefix.toString()
  }
}
