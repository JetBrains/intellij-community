// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.testAssertions

import com.intellij.python.testAssertions.PyTestAssertionSyntax.COMMENT_CHAR
import com.intellij.python.testAssertions.PyTestAssertionSyntax.MARKER_LEFT
import com.intellij.python.testAssertions.PyTestAssertionSyntax.NEWLINE
import com.intellij.python.testAssertions.PyTestAssertionSyntax.indexOfUnescapedCommentChar
import com.intellij.python.testAssertions.PyTestAssertionSyntax.unescape
import org.jetbrains.annotations.ApiStatus

/**
 * A problem in the assertion syntax of a test text.
 *
 * [line] and [column] start at 0. They are the position of the `#` of the assertion.
 * [start] and [end] are the offsets of the text with the problem. [end] is exclusive.
 * [fix] is the edit that removes the problem, when the parser knows one.
 */
@ApiStatus.Internal
data class PyTestAssertionDiagnostic(
  val line: Int,
  val column: Int,
  val message: String,
  val start: Int,
  val end: Int,
  val fix: PyTestAssertionFix? = null,
) {
  override fun toString(): String = "${line + 1}:${column + 1}: $message"
}

/** An edit that removes a [PyTestAssertionDiagnostic]. The text from [start] to [end] becomes [replacement]. [end] is exclusive. */
@ApiStatus.Internal
data class PyTestAssertionFix(val kind: Kind, val start: Int, val end: Int, val replacement: String) {
  enum class Kind {
    /** Replaces a misspelled directive name with the name that the parser suggests. */
    DIRECTIVE_NAME,

    /** Writes the assertions of one code line in the canonical form. */
    CANONICAL_FORM,
  }

  /** Applies this edit to [text]. */
  fun applyTo(text: String): String = text.replaceRange(start, end, replacement)
}

/** A part of an assertion that an editor colours. [start] and [end] are offsets in the test text. [end] is exclusive. */
@ApiStatus.Internal
data class PyTestAssertionToken(val start: Int, val end: Int, val kind: Kind) {
  enum class Kind {
    MARKER,
    DIRECTIVE,
    MODIFIER,
  }
}

/**
 * The assertions of a test text and the problems that the parser found in it.
 *
 * [tokens] are the markers, the known directive names and the modifiers, in the order of the text.
 */
@ApiStatus.Internal
data class PyTestAssertionParseResult(
  val assertions: List<PyTestAssertion>,
  val diagnostics: List<PyTestAssertionDiagnostic>,
  val tokens: List<PyTestAssertionToken>,
)

@ApiStatus.Internal
object PyTestAssertionParser {

  /**
   * Blanks out every assertion's text with spaces so it isn't seen as a comment and doesn't affect parsing.
   */
  fun maskAssertions(code: String, assertions: List<PyTestAssertion>): String {
    val chars = code.toCharArray()
    for (assertion in assertions) {
      for (i in assertion.assertionOffsetStart until assertion.assertionOffsetEnd) {
        if (chars[i] != NEWLINE) chars[i] = ' '
      }
    }
    return String(chars)
  }

  fun parseAssertions(code: String): List<PyTestAssertion> = parse(code).assertions

  /**
   * Parses the assertions of [code] and reports the problems in their syntax.
   *
   * An inline comment whose first word is not an assertion directive is a plain comment. A marker line must hold a directive.
   * When the syntax has no problem, the parser also reports each code line whose assertions are not in the canonical form.
   */
  fun parse(code: String): PyTestAssertionParseResult {
    val result = parseSyntax(code)
    if (result.diagnostics.isNotEmpty()) return result
    return result.copy(diagnostics = canonicalFormDiagnostics(code, result.assertions))
  }

  /**
   * Returns a diagnostic for each code line whose assertions [PyTestAssertionInliner] writes in another form.
   *
   * The test compares the written text with [code]. So such a test fails, also with a correct result.
   */
  private fun canonicalFormDiagnostics(code: String, assertions: List<PyTestAssertion>): List<PyTestAssertionDiagnostic> =
    assertions.groupBy { it.codeLineStart }.values.mapNotNull { lineAssertions ->
      val written = PyTestAssertionInliner.generateActualText(code, lineAssertions, lineAssertions)
      if (written == code) return@mapNotNull null
      val prefix = code.commonPrefixWith(written).length
      val suffix = minOf(code.commonSuffixWith(written).length, minOf(code.length, written.length) - prefix)
      val fix = PyTestAssertionFix(PyTestAssertionFix.Kind.CANONICAL_FORM, prefix, code.length - suffix,
                                   written.substring(prefix, written.length - suffix))
      val first = lineAssertions.minBy { it.assertionOffsetStart }
      PyTestAssertionDiagnostic(first.assertionLineStart, first.assertionColumnStart,
                                "The assertions of this code line are not in the canonical form, so a correct result fails the test",
                                first.assertionOffsetStart, lineAssertions.maxOf { it.assertionOffsetEnd }, fix)
    }

  private fun parseSyntax(code: String): PyTestAssertionParseResult {
    val lines = code.split(NEWLINE)
    val lineStartOffsets = computeLineStartOffsets(lines)
    val result = mutableListOf<PyTestAssertion>()
    val diagnostics = mutableListOf<PyTestAssertionDiagnostic>()
    val tokens = mutableListOf<PyTestAssertionToken>()
    // The last line that a marker can point at: a code line or a plain comment. A marker line or a payload line is never a target.
    var targetLine = -1

    var lineIndex = 0
    while (lineIndex < lines.size) {
      val parsedStart = parseAssertionStart(lines, lineIndex, targetLine, lineStartOffsets, diagnostics)
      if (parsedStart == null) {
        if (!isMarkerLine(lines[lineIndex])) targetLine = lineIndex
        lineIndex++
        continue
      }
      if (parsedStart.codeColumnStart == -1) targetLine = lineIndex
      val directive = PyTestDirectives.findAssertion(parsedStart.type)
      if (parsedStart.codeColumnStart == -1 && directive == null) {
        if (PyTestDirectives.findNearMiss(parsedStart.type) != null) {
          diagnostics += unknownDirective(lineStartOffsets, parsedStart)
        }
        lineIndex++
        continue
      }
      if (directive == null) {
        diagnostics += unknownDirective(lineStartOffsets, parsedStart)
      }
      else {
        tokens += token(lineStartOffsets, lineIndex, parsedStart.typeColumns, PyTestAssertionToken.Kind.DIRECTIVE)
      }

      val payload = collectPayload(lines, parsedStart, directive, lineStartOffsets, tokens)
      val assertion = buildAssertion(parsedStart, payload)
      result += assertion
      directive?.checkPayload(assertion.content)?.let {
        val columns = parsedStart.typeColumns.first until lines[lineIndex].length
        diagnostics += diagnostic(lineStartOffsets, lineIndex, parsedStart.assertionColumnStart, it, columns)
      }

      lineIndex = payload.nextLineIndex
    }

    return PyTestAssertionParseResult(result, diagnostics, tokens.sortedBy { it.start })
  }

  private fun unknownDirective(lineStartOffsets: IntArray, parsedStart: ParsedAssertionStart): PyTestAssertionDiagnostic {
    val nearMiss = PyTestDirectives.findNearMiss(parsedStart.type)
    val hint = if (nearMiss != null) ". Did you mean '${nearMiss.name}'?" else ""
    return diagnostic(lineStartOffsets, parsedStart.assertionLineStart, parsedStart.assertionColumnStart,
                      "Unknown directive '${parsedStart.type}'$hint", parsedStart.typeColumns, nearMiss?.name)
  }

  /** [columns] is the text with the problem on line [lineIndex]. [replacement] is the directive name that replaces it. */
  private fun diagnostic(
    lineStartOffsets: IntArray,
    lineIndex: Int,
    column: Int,
    message: String,
    columns: IntRange,
    replacement: String? = null,
  ): PyTestAssertionDiagnostic {
    val start = lineStartOffsets[lineIndex] + columns.first
    val end = lineStartOffsets[lineIndex] + columns.end
    val fix = replacement?.let { PyTestAssertionFix(PyTestAssertionFix.Kind.DIRECTIVE_NAME, start, end, it) }
    return PyTestAssertionDiagnostic(lineIndex, column, message, start, end, fix)
  }

  private fun token(lineStartOffsets: IntArray, lineIndex: Int, columns: IntRange, kind: PyTestAssertionToken.Kind) =
    PyTestAssertionToken(lineStartOffsets[lineIndex] + columns.first, lineStartOffsets[lineIndex] + columns.end, kind)

  private fun computeLineStartOffsets(lines: List<String>): IntArray {
    val offsets = IntArray(lines.size)
    var currentOffset = 0

    for (i in lines.indices) {
      offsets[i] = currentOffset
      currentOffset += lines[i].length + 1
    }

    return offsets
  }

  private fun buildAssertion(parsedStart: ParsedAssertionStart, payload: CollectedPayload): PyTestAssertion {
    return PyTestAssertion(
      codeOffsetStart = parsedStart.codeOffset,
      codeLineStart = parsedStart.codeLine,
      codeColumnStart = parsedStart.codeColumnStart,
      codeColumnEnd = parsedStart.codeColumnEnd,
      assertionOffsetStart = parsedStart.assertionOffsetStart,
      assertionOffsetEnd = payload.assertionOffsetEnd,
      assertionLineStart = parsedStart.assertionLineStart,
      assertionLineEnd = payload.assertionLineEnd,
      assertionColumnStart = parsedStart.assertionColumnStart,
      type = parsedStart.type,
      content = payload.content,
      fixmeContent = payload.fixmeContent,
      comment = payload.comment.ifEmpty { null },
    )
  }

  private fun parseAssertionStart(
    lines: List<String>,
    lineIndex: Int,
    targetLine: Int,
    lineStartOffsets: IntArray,
    diagnostics: MutableList<PyTestAssertionDiagnostic>,
  ): ParsedAssertionStart? {
    val head = PyTestAssertionHead.scan(lines[lineIndex]) ?: return null

    return if (head.isCommentLine) {
      parseCommentOnlyAssertion(lines, lineIndex, head, targetLine, lineStartOffsets, diagnostics)
    }
    else {
      parseInlineAssertion(lineIndex, head, lineStartOffsets)
    }
  }

  /** Returns true when the comment line [lineIndex] starts a new assertion, so it does not continue the payload above it. */
  private fun isAssertionStartLine(lines: List<String>, lineIndex: Int): Boolean =
    PyTestAssertionHead.scan(lines[lineIndex])?.startsMarkerAssertion == true

  private fun isMarkerLine(line: String): Boolean = PyTestAssertionHead.scan(line)?.marker != null

  private fun parseInlineAssertion(lineIndex: Int, head: PyTestAssertionHead, lineStartOffsets: IntArray): ParsedAssertionStart? {
    val name = head.name ?: return null
    if (name.isEmpty()) return null

    return ParsedAssertionStart(
      codeOffset = lineStartOffsets[lineIndex],
      codeLine = lineIndex,
      codeColumnStart = -1,
      codeColumnEnd = -1,
      assertionOffsetStart = lineStartOffsets[lineIndex] + head.hashColumn,
      assertionLineStart = lineIndex,
      assertionColumnStart = head.hashColumn,
      type = head.nameText,
      typeColumns = name,
    )
  }

  private fun parseCommentOnlyAssertion(
    lines: List<String>,
    lineIndex: Int,
    head: PyTestAssertionHead,
    targetLine: Int,
    lineStartOffsets: IntArray,
    diagnostics: MutableList<PyTestAssertionDiagnostic>,
  ): ParsedAssertionStart? {
    val marker = head.marker ?: return null
    val line = lines[lineIndex]
    val hashIndex = head.hashColumn

    val name = head.name
    if (name == null || !head.startsMarkerAssertion) {
      diagnostics += diagnostic(lineStartOffsets, lineIndex, hashIndex, "The marker has no directive", marker)
      return null
    }
    if (targetLine < 0) {
      diagnostics += diagnostic(lineStartOffsets, lineIndex, hashIndex, "The marker has no code line above it", hashIndex until line.length)
      return null
    }

    val startsLeft = line[marker.first] == MARKER_LEFT
    if (startsLeft && marker.length > 1) {
      val message = "A span cannot start with '$MARKER_LEFT'. Start the span in the column after the '#'"
      diagnostics += diagnostic(lineStartOffsets, lineIndex, hashIndex, message, marker)
    }
    val codeColumnStart = marker.first - if (startsLeft) 1 else 0
    if (codeColumnStart > lines[targetLine].length) {
      val message = "The marker is right of the end of the code line ${targetLine + 1}"
      diagnostics += diagnostic(lineStartOffsets, lineIndex, hashIndex, message, marker)
    }
    val codeColumnEnd = if (marker.length > 1) marker.end else -1

    return ParsedAssertionStart(
      codeOffset = lineStartOffsets[targetLine] + codeColumnStart,
      codeLine = targetLine,
      codeColumnStart = codeColumnStart,
      codeColumnEnd = codeColumnEnd,
      assertionOffsetStart = lineStartOffsets[lineIndex] + hashIndex,
      assertionLineStart = lineIndex,
      assertionColumnStart = hashIndex,
      type = head.nameText,
      typeColumns = name,
      markerColumns = marker,
    )
  }

  /**
   * Collects the payload of the assertion that starts in [parsedStart], from its first line and the comment lines below it.
   *
   * Each payload line splits into a body and a trailing comment. The bodies before `FIXME` are the content, and the bodies after it
   * are the fix. A further payload line is a comment line with its `#` in the column of the `#` of the assertion.
   * The modifiers of a known [directive] go to [tokens].
   */
  private fun collectPayload(
    lines: List<String>,
    parsedStart: ParsedAssertionStart,
    directive: PyTestDirective?,
    lineStartOffsets: IntArray,
    tokens: MutableList<PyTestAssertionToken>,
  ): CollectedPayload {
    val contentLines = mutableListOf<String>()
    var fixmeLines: MutableList<String>? = null
    val commentLines = mutableListOf<String>()
    parsedStart.markerColumns?.let { tokens += token(lineStartOffsets, parsedStart.assertionLineStart, it, PyTestAssertionToken.Kind.MARKER) }

    fun modifier(lineIndex: Int, columns: IntRange) {
      if (directive != null) tokens += token(lineStartOffsets, lineIndex, columns, PyTestAssertionToken.Kind.MODIFIER)
    }

    fun readPayloadLine(lineIndex: Int, startColumn: Int) {
      val lineSplit = splitTrailingComment(lines[lineIndex].substring(startColumn))
      commentLines += lineSplit.comment.orEmpty()
      val body = lineSplit.beforeComment
      if (lineIndex == parsedStart.assertionLineStart && directive?.isSeverity == true) {
        val word = PyTestLineCursor(body).skipSpaces().word()
        if (body.substring(word) == PyTestDirectives.TOOLTIP.name) modifier(lineIndex, word.shift(startColumn))
      }

      val currentFixmeLines = fixmeLines
      if (currentFixmeLines != null) {
        currentFixmeLines += body.trim()
        return
      }
      val split = splitAtFixme(body)
      if (split.fixme == null) {
        contentLines += split.beforeFixme
        return
      }
      modifier(lineIndex, split.fixme.shift(startColumn))
      if (split.beforeFixme.isNotEmpty()) contentLines += split.beforeFixme
      fixmeLines = mutableListOf(split.afterFixme)
    }

    val startLine = parsedStart.assertionLineStart
    val assertionColumnStart = parsedStart.assertionColumnStart
    readPayloadLine(startLine, parsedStart.typeColumns.end)

    var lineIndex = startLine + 1
    var assertionLineEnd = startLine
    var assertionOffsetEnd = computeLineEndOffset(lineStartOffsets, startLine, lines[startLine])

    while (lineIndex < lines.size) {
      val line = lines[lineIndex]
      if (!isCommentLine(line)) break
      if (line.indexOf(COMMENT_CHAR) != assertionColumnStart) break
      if (isAssertionStartLine(lines, lineIndex)) break

      readPayloadLine(lineIndex, assertionColumnStart + 1)

      assertionLineEnd = lineIndex
      assertionOffsetEnd = computeLineEndOffset(lineStartOffsets, lineIndex, line)
      lineIndex++
    }

    return CollectedPayload(
      content = unescape(contentLines.joinInnerLines()),
      fixmeContent = fixmeLines?.let { unescape(it.joinInnerLines()) },
      comment = commentLines.dropLastWhile { it.isEmpty() }.joinToString(NEWLINE.toString()),
      nextLineIndex = lineIndex,
      assertionLineEnd = assertionLineEnd,
      assertionOffsetEnd = assertionOffsetEnd,
    )
  }

  /** Joins the lines with a newline. An empty line inside is kept, and an empty line at the start or at the end is dropped. */
  private fun List<String>.joinInnerLines(): String =
    dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }.joinToString(NEWLINE.toString())

  private fun IntRange.shift(delta: Int): IntRange = first + delta..last + delta

  private fun splitAtFixme(text: String): FixmeSplit {
    val match = fixmeWord.find(text)
    return if (match != null) {
      FixmeSplit(
        beforeFixme = text.substring(0, match.range.first).trim(),
        afterFixme = text.substring(match.range.end).trim(),
        fixme = match.range,
      )
    }
    else {
      FixmeSplit(
        beforeFixme = text.trim(),
        afterFixme = "",
        fixme = null,
      )
    }
  }

  private fun splitTrailingComment(text: String): CommentSplit {
    val idx = indexOfUnescapedCommentChar(text)
    return if (idx >= 0) {
      CommentSplit(
        beforeComment = text.substring(0, idx),
        comment = text.substring(idx + 1).trim(),
      )
    }
    else {
      CommentSplit(
        beforeComment = text,
        comment = null,
      )
    }
  }

  private fun isCommentLine(line: String): Boolean {
    return line.trimStart().startsWith(COMMENT_CHAR)
  }

  private fun computeLineEndOffset(lineStartOffsets: IntArray, lineIndex: Int, line: String): Int {
    return lineStartOffsets[lineIndex] + line.length
  }


  private data class ParsedAssertionStart(
    val codeOffset: Int,
    val codeLine: Int,
    val codeColumnStart: Int,
    val codeColumnEnd: Int,
    val assertionOffsetStart: Int,
    val assertionLineStart: Int,
    val assertionColumnStart: Int,
    val type: String,
    /** The columns of [type] on the line of the assertion. The payload starts at its end. */
    val typeColumns: IntRange,
    val markerColumns: IntRange? = null,
  )

  /** [fixme] holds the columns of the word `FIXME` in the split text, or null without it. */
  private data class FixmeSplit(
    val beforeFixme: String,
    val afterFixme: String,
    val fixme: IntRange?,
  )

  private data class CommentSplit(
    val beforeComment: String,
    val comment: String?,
  )

  private data class CollectedPayload(
    val content: String,
    val fixmeContent: String?,
    val comment: String,
    val nextLineIndex: Int,
    val assertionLineEnd: Int,
    val assertionOffsetEnd: Int,
  )
}

private val fixmeWord = Regex("""(?<!\S)${PyTestDirectives.FIXME.name}(?!\S)""")
