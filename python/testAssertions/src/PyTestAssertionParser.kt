// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.testAssertions

import com.intellij.python.testAssertions.PyTestAssertionSyntax.COMMENT_CHAR
import com.intellij.python.testAssertions.PyTestAssertionSyntax.MARKER_LEFT
import com.intellij.python.testAssertions.PyTestAssertionSyntax.NEWLINE
import com.intellij.python.testAssertions.PyTestAssertionSyntax.isAssertionMarker
import com.intellij.python.testAssertions.PyTestAssertionSyntax.scanTokenEnd
import com.intellij.python.testAssertions.PyTestAssertionSyntax.skipWhitespace
import com.intellij.python.testAssertions.PyTestAssertionSyntax.skipWhitespaceAndGuides

/** A problem in the assertion syntax of a test text. [line] and [column] start at 0. */
data class PyTestAssertionDiagnostic(val line: Int, val column: Int, val message: String) {
  override fun toString(): String = "${line + 1}:${column + 1}: $message"
}

/** The assertions of a test text and the problems that the parser found in it. */
data class PyTestAssertionParseResult(
  val assertions: List<PyTestAssertion>,
  val diagnostics: List<PyTestAssertionDiagnostic>,
)

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
   */
  fun parse(code: String): PyTestAssertionParseResult {
    val lines = code.split(NEWLINE)
    val lineStartOffsets = computeLineStartOffsets(lines)
    val result = mutableListOf<PyTestAssertion>()
    val diagnostics = mutableListOf<PyTestAssertionDiagnostic>()

    var lineIndex = 0
    while (lineIndex < lines.size) {
      val parsedStart = parseAssertionStart(lines, lineIndex, lineStartOffsets, diagnostics)
      if (parsedStart == null) {
        lineIndex++
        continue
      }
      val directive = PyTestDirectives.findAssertion(parsedStart.type)
      if (parsedStart.codeColumnStart == -1 && directive == null) {
        PyTestDirectives.findNearMiss(parsedStart.type)?.let {
          diagnostics += diagnostic(parsedStart, "Unknown directive '${parsedStart.type}'. Did you mean '${it.name}'?")
        }
        lineIndex++
        continue
      }

      val payload = collectPayload(lines, lineIndex, parsedStart.initialPayload, lineStartOffsets, parsedStart.assertionColumnStart)
      val assertion = buildAssertion(parsedStart, payload)
      result += assertion
      if (directive == null) {
        val nearMiss = PyTestDirectives.findNearMiss(parsedStart.type)
        val hint = if (nearMiss != null) ". Did you mean '${nearMiss.name}'?" else ""
        diagnostics += diagnostic(parsedStart, "Unknown directive '${parsedStart.type}'$hint")
      }
      else {
        directive.checkPayload(assertion.content)?.let { diagnostics += diagnostic(parsedStart, it) }
      }

      lineIndex = payload.nextLineIndex
    }

    return PyTestAssertionParseResult(result, diagnostics)
  }

  private fun diagnostic(parsedStart: ParsedAssertionStart, message: String): PyTestAssertionDiagnostic =
    PyTestAssertionDiagnostic(parsedStart.assertionLineStart, parsedStart.assertionColumnStart, message)

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
      assertionColumnEnd = payload.assertionColumnEnd,
      type = parsedStart.type,
      content = payload.content,
      fixmeContent = payload.fixmeContent,
      comment = payload.comment.ifEmpty { null },
    )
  }

  private fun parseAssertionStart(
    lines: List<String>,
    lineIndex: Int,
    lineStartOffsets: IntArray,
    diagnostics: MutableList<PyTestAssertionDiagnostic>,
  ): ParsedAssertionStart? {
    val line = lines[lineIndex]
    val hashIndex = line.indexOf(COMMENT_CHAR)
    if (hashIndex < 0) return null

    val beforeHash = line.substring(0, hashIndex)
    val afterHash = line.substring(hashIndex + 1)

    return if (beforeHash.isBlank()) {
      parseCommentOnlyAssertion(lines, lineIndex, hashIndex, afterHash, lineStartOffsets, diagnostics)
    }
    else {
      parseInlineAssertion(lineIndex, hashIndex, afterHash, lineStartOffsets)
    }
  }

  private fun isAssertionStartLine(lines: List<String>, lineIndex: Int): Boolean {
    val line = lines[lineIndex]
    val hashIndex = line.indexOf(COMMENT_CHAR)
    if (hashIndex < 0) return false

    val beforeHash = line.substring(0, hashIndex)
    val afterHash = line.substring(hashIndex + 1)

    return if (beforeHash.isBlank()) {
      isCommentOnlyAssertionStart(afterHash)
    }
    else {
      isInlineAssertionStart(afterHash)
    }
  }

  private fun parseInlineAssertion(lineIndex: Int, hashIndex: Int, afterHash: String, lineStartOffsets: IntArray): ParsedAssertionStart? {
    var cursor = skipWhitespace(afterHash, 0)
    if (cursor >= afterHash.length) return null
    if (isAssertionMarker(afterHash[cursor])) return null

    val typeStart = cursor
    cursor = scanTokenEnd(afterHash, cursor)
    if (cursor <= typeStart) return null

    val type = afterHash.substring(typeStart, cursor)
    val payload = afterHash.substring(cursor).trimStart()

    val assertionColumnEnd = lineIndexColumnEnd(hashIndex, afterHash)
    val assertionOffsetStart = lineStartOffsets[lineIndex] + hashIndex

    val codeColumnStart = -1
    val codeColumnEnd = -1
    val codeOffset = lineStartOffsets[lineIndex]

    return ParsedAssertionStart(
      codeOffset = codeOffset,
      codeLine = lineIndex,
      codeColumnStart = codeColumnStart,
      codeColumnEnd = codeColumnEnd,
      assertionOffsetStart = assertionOffsetStart,
      assertionLineStart = lineIndex,
      assertionColumnStart = hashIndex,
      assertionColumnEnd = assertionColumnEnd,
      type = type,
      initialPayload = payload,
    )
  }

  private fun parseCommentOnlyAssertion(
    lines: List<String>,
    lineIndex: Int,
    hashIndex: Int,
    afterHash: String,
    lineStartOffsets: IntArray,
    diagnostics: MutableList<PyTestAssertionDiagnostic>,
  ): ParsedAssertionStart? {
    val cursor = skipWhitespaceAndGuides(afterHash)
    if (cursor >= afterHash.length) return null

    val marker = afterHash[cursor]
    if (!isAssertionMarker(marker)) return null

    val codeColumnStart = hashIndex + 1 + cursor - (if (marker == MARKER_LEFT) 1 else 0)

    var scanIndex = cursor + 1
    var codeColumnEnd = -1
    while (scanIndex < afterHash.length && isAssertionMarker(afterHash[scanIndex])) {
      scanIndex++
      codeColumnEnd = hashIndex + 1 + scanIndex
    }

    scanIndex = skipWhitespace(afterHash, scanIndex)
    if (scanIndex >= afterHash.length) return null

    val typeStart = scanIndex
    scanIndex = scanTokenEnd(afterHash, scanIndex)
    if (scanIndex <= typeStart) return null

    val type = afterHash.substring(typeStart, scanIndex)
    val payload = afterHash.substring(scanIndex).trimStart()
    val codeLine = findReferencedCodeLine(lines, lineIndex)
    if (codeLine == null) {
      diagnostics += PyTestAssertionDiagnostic(lineIndex, hashIndex, "The marker has no code line above it")
      return null
    }
    val codeOffset = lineStartOffsets[codeLine] + codeColumnStart

    val assertionColumnEnd = lineIndexColumnEnd(hashIndex, afterHash)
    val assertionOffsetStart = lineStartOffsets[lineIndex] + hashIndex

    return ParsedAssertionStart(
      codeOffset = codeOffset,
      codeLine = codeLine,
      codeColumnStart = codeColumnStart,
      codeColumnEnd = codeColumnEnd,
      assertionOffsetStart = assertionOffsetStart,
      assertionLineStart = lineIndex,
      assertionColumnStart = hashIndex,
      assertionColumnEnd = assertionColumnEnd,
      type = type,
      initialPayload = payload,
    )
  }

  private fun collectPayload(
    lines: List<String>,
    assertionStartLineIndex: Int,
    initialPayload: String,
    lineStartOffsets: IntArray,
    assertionColumnStart: Int,
  ): CollectedPayload {
    val contentBuilder = StringBuilder()
    val fixmeBuilder = StringBuilder()
    val commentBuilder = StringBuilder()
    var inFixme = false

    val firstLineSplit = splitTrailingComment(initialPayload)
    val firstSplit = splitAtFixme(firstLineSplit.beforeComment)
    appendIfNotEmpty(contentBuilder, firstSplit.beforeFixme)
    if (firstSplit.afterFixme != null) {
      inFixme = true
      appendIfNotEmpty(fixmeBuilder, firstSplit.afterFixme)
    }
    appendIfNotEmpty(commentBuilder, firstLineSplit.comment)

    var lineIndex = assertionStartLineIndex + 1
    var assertionLineEnd = assertionStartLineIndex
    var assertionColumnEnd = lineIndexColumnEnd(
      lines[assertionStartLineIndex].indexOf(COMMENT_CHAR),
      lines[assertionStartLineIndex].substring(lines[assertionStartLineIndex].indexOf(COMMENT_CHAR) + 1)
    )
    var assertionOffsetEnd = computeLineEndOffset(
      lineStartOffsets,
      assertionStartLineIndex,
      lines[assertionStartLineIndex]
    )

    while (lineIndex < lines.size) {
      val line = lines[lineIndex]
      if (!isCommentLine(line)) break
      if (line.indexOf(COMMENT_CHAR) != assertionColumnStart) break
      if (isAssertionStartLine(lines, lineIndex)) break

      val continuationPayload = extractCommentPayload(line)
      val continuationSplit = splitTrailingComment(continuationPayload)

      if (!inFixme) {
        val split = splitAtFixme(continuationSplit.beforeComment)
        appendIfNotEmpty(contentBuilder, split.beforeFixme)
        if (split.afterFixme != null) {
          inFixme = true
          appendIfNotEmpty(fixmeBuilder, split.afterFixme)
        }
      }
      else {
        appendIfNotEmpty(fixmeBuilder, continuationSplit.beforeComment.trimEnd())
      }

      appendIfNotEmpty(commentBuilder, continuationSplit.comment)

      assertionLineEnd = lineIndex
      assertionColumnEnd = lines[lineIndex].length
      assertionOffsetEnd = computeLineEndOffset(lineStartOffsets, lineIndex, lines[lineIndex])
      lineIndex++
    }

    return CollectedPayload(
      content = contentBuilder.toString(),
      fixmeContent = if (inFixme) fixmeBuilder.toString() else null,
      comment = commentBuilder.toString(),
      nextLineIndex = lineIndex,
      assertionLineEnd = assertionLineEnd,
      assertionColumnEnd = assertionColumnEnd,
      assertionOffsetEnd = assertionOffsetEnd,
    )
  }

  private fun splitAtFixme(text: String): FixmeSplit {
    val keyword = PyTestDirectives.FIXME.name
    val idx = text.indexOf(keyword)
    return if (idx >= 0) {
      FixmeSplit(
        beforeFixme = text.substring(0, idx).trimEnd(),
        afterFixme = text.substring(idx + keyword.length).trimStart(),
      )
    }
    else {
      FixmeSplit(
        beforeFixme = text.trimEnd(),
        afterFixme = null,
      )
    }
  }

  private fun splitTrailingComment(text: String): CommentSplit {
    val idx = text.indexOf(COMMENT_CHAR)
    return if (idx >= 0) {
      CommentSplit(
        beforeComment = text.substring(0, idx).trimEnd(),
        comment = text.substring(idx + 1).trim(),
      )
    }
    else {
      CommentSplit(
        beforeComment = text.trimEnd(),
        comment = null,
      )
    }
  }

  private fun appendIfNotEmpty(builder: StringBuilder, text: String?) {
    if (text.isNullOrEmpty()) return
    if (builder.isNotEmpty()) builder.append(NEWLINE)
    builder.append(text)
  }

  private fun extractCommentPayload(line: String): String {
    val hashIndex = line.indexOf(COMMENT_CHAR)
    if (hashIndex < 0) return ""
    return line.substring(hashIndex + 1).trimStart()
  }

  private fun isCommentLine(line: String): Boolean {
    return line.trimStart().startsWith(COMMENT_CHAR)
  }

  private fun isInlineAssertionStart(afterHash: String): Boolean {
    val cursor = skipWhitespace(afterHash, 0)
    if (cursor >= afterHash.length) return false
    return !isAssertionMarker(afterHash[cursor])
  }

  private fun isCommentOnlyAssertionStart(afterHash: String): Boolean {
    val cursor = skipWhitespaceAndGuides(afterHash)
    if (cursor >= afterHash.length) return false
    return isAssertionMarker(afterHash[cursor])
  }

  private fun findReferencedCodeLine(lines: List<String>, assertionLineIndex: Int): Int? {
    var i = assertionLineIndex - 1
    while (i >= 0) {
      if (!isCommentLine(lines[i]) || !isAssertionStartLine(lines, i)) {
        return i
      }
      i--
    }
    return null
  }

  private fun lineIndexColumnEnd(hashIndex: Int, afterHash: String): Int {
    return hashIndex + afterHash.length
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
    val assertionColumnEnd: Int,
    val type: String,
    val initialPayload: String,
  )

  private data class FixmeSplit(
    val beforeFixme: String,
    val afterFixme: String?,
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
    val assertionColumnEnd: Int,
    val assertionOffsetEnd: Int,
  )
}
