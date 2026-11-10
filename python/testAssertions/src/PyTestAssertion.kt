// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.testAssertions

import com.intellij.python.testAssertions.PyTestAssertionSyntax.COMMENT_CHAR
import com.intellij.python.testAssertions.PyTestAssertionSyntax.GUIDE_BAR
import com.intellij.python.testAssertions.PyTestAssertionSyntax.MARKER_CORNER
import com.intellij.python.testAssertions.PyTestAssertionSyntax.MARKER_LEFT
import com.intellij.python.testAssertions.PyTestAssertionSyntax.MARKER_SPAN
import com.intellij.python.testAssertions.PyTestAssertionSyntax.NEWLINE
import org.jetbrains.annotations.ApiStatus

/**
 * One assertion of a test text: an expected one from the parser, or an actual one from the engine.
 *
 * The code position is the checked element. The assertion position is the comment that holds the assertion.
 * A column of -1 means "not set". An inline assertion has [codeColumnStart] -1 and applies to the whole line.
 */
@ApiStatus.Internal
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
  /** The name of a [PyTestDirective], or an unknown name from a wrong test text. */
  val type: String,
  val content: String,
  val fixmeContent: String? = null,
  /**
   * The trailing comments of the payload lines, separated by a newline.
   *
   * The comment of each payload line stays on that line, so an empty part is a payload line without a comment.
   */
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

  /**
   * Writes this assertion as comment text.
   *
   * [hashColumn] is the column of the `#`. A marker line keeps it, so an indented marker line stays indented.
   * The continuation lines of a multi-line payload also start at it.
   * The marker of the assertion must not be left of [hashColumn].
   * A marker in the column of the `#` is a `\`. A span from that column starts in the next column, because a span cannot start with a `\`.
   */
  fun asText(serializedAssertionsAbove: List<String> = emptyList(), hashColumn: Int = 0): Pair<String, List<String>> {
    val normalizedCodeColumnStart = if (codeColumnStart >= 0 && codeColumnStart == hashColumn) hashColumn + 1 else codeColumnStart

    val serializedAssertionsAboveCopy = serializedAssertionsAbove.toMutableList()
    if (normalizedCodeColumnStart > 0) {
      for (i in serializedAssertionsAbove.indices) {
        serializedAssertionsAboveCopy[i] = insertGuideAtColumn(serializedAssertionsAbove[i], normalizedCodeColumnStart)
      }
    }

    return buildAssertionText(normalizedCodeColumnStart, hashColumn) to serializedAssertionsAboveCopy
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

  private fun buildAssertionText(normalizedCodeColumnStart: Int, hashColumn: Int): String {
    val suffix = buildSuffixLines().withIndex().joinToString("") { (index, line) ->
      when {
        index > 0 -> "$NEWLINE${PyTestAssertionSyntax.continuationPrefix(hashColumn, line)}$line"
        line.isEmpty() -> ""
        else -> " $line"
      }
    }

    if (normalizedCodeColumnStart < 0) {
      return "# $type$suffix"
    }

    val prefix = buildMarkerPrefix(normalizedCodeColumnStart, hashColumn, codeColumnEndEffective)
    return "${" ".repeat(hashColumn)}#$prefix $type$suffix"
  }

  /** Returns the payload lines, each with its trailing comment. */
  private fun buildSuffixLines(): List<String> {
    val body = buildString {
      if (content.isNotBlank()) {
        append(PyTestAssertionSyntax.escape(content))
      }
      if (fixmeContent != null) {
        if (isNotEmpty()) append(" ")
        append(PyTestDirectives.FIXME.name)
        if (fixmeContent.isNotBlank()) {
          append(" ")
          append(PyTestAssertionSyntax.escape(fixmeContent))
        }
      }
    }
    val bodyLines = body.split(NEWLINE)
    val commentLines = comment?.split(NEWLINE).orEmpty()
    return List(maxOf(bodyLines.size, commentLines.size)) { index ->
      val bodyLine = bodyLines.getOrElse(index) { "" }
      val commentLine = commentLines.getOrNull(index)?.takeIf { it.isNotBlank() }
      when {
        commentLine == null -> bodyLine
        bodyLine.isEmpty() -> "$COMMENT_CHAR $commentLine"
        else -> "$bodyLine $COMMENT_CHAR $commentLine"
      }
    }
  }

  private fun buildMarkerPrefix(normalizedCodeColumnStart: Int, hashColumn: Int, codeColumnEnd: Int): String {
    val prefix = StringBuilder()

    repeat(normalizedCodeColumnStart - hashColumn - 1) {
      prefix.append(' ')
    }

    val spanLength = if (codeColumnEnd > 0) (codeColumnEnd - normalizedCodeColumnStart).coerceAtLeast(1) else 1
    when (spanLength) {
      1 if (codeColumnStart == hashColumn) -> prefix.append(MARKER_LEFT)
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
