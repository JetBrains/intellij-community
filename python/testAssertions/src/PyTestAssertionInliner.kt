// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.testAssertions

import com.intellij.python.testAssertions.PyTestAssertionSyntax.NEWLINE
import org.jetbrains.annotations.ApiStatus
import kotlin.math.abs

/**
 * Writes actual assertions into a test text in place of the expected ones.
 *
 * When the actual assertions equal the expected ones, the result equals the original text. So a test compares two texts,
 * and a failure shows the full actual text.
 */
@ApiStatus.Internal
object PyTestAssertionInliner {

  fun generateActualText(originalText: String, expectedAssertions: List<PyTestAssertion>, actualAssertions: List<PyTestAssertion>): String {
    val counterparts = findCounterparts(expectedAssertions, actualAssertions)
    val expectedByCodeLine = expectedAssertions.groupBy { it.codeLineStart }
    val actualByCodeLine = actualAssertions.groupBy { it.codeLineStart }
    val actualText = positionActualAssertions(originalText, expectedByCodeLine, actualByCodeLine, counterparts)
    return actualText
  }

  /** Maps each actual assertion to the expected assertion that it answers. */
  fun findCounterparts(
    expectedAssertions: List<PyTestAssertion>,
    actualAssertions: List<PyTestAssertion>,
  ): Map<PyTestAssertion, PyTestAssertion> {

    val matches = mutableMapOf<PyTestAssertion, PyTestAssertion>()
    val usedExpected = mutableSetOf<PyTestAssertion>()

    for (actual in actualAssertions) {
      val expected = findBestCounterpart(actual, expectedAssertions, usedExpected) ?: continue
      matches[actual] = expected
      if (!expected.isWildcard) usedExpected += expected
    }

    return matches
  }

  private fun findBestCounterpart(
    actual: PyTestAssertion,
    expectedAssertions: List<PyTestAssertion>,
    usedExpected: Set<PyTestAssertion>,
  ): PyTestAssertion? {
    return expectedAssertions
      .filter { it !in usedExpected && areCounterparts(it, actual) }
      .minByOrNull { expected ->
        when {
          expected.content == actual.content -> 0
          expected.isWildcard -> 1
          else -> abs(expected.codeColumnStart - actual.codeColumnStart) + 2
        }
      }
  }

  private fun areCounterparts(expected: PyTestAssertion, actual: PyTestAssertion): Boolean {
    if (expected.isWildcard && expected.type != actual.type) {
      if (!actual.isSeverity) return false
    }
    else if (expected.type != actual.type) return false
    if (expected.codeColumnStart == -1) {
      return expected.codeLineStart == actual.codeLineStart
    }
    if (expected.codeOffsetStart < actual.codeOffsetStart || expected.codeOffsetStart > actual.codeOffsetStart + actual.columnLength) return false

    if (expected.codeColumnEndEffective > -1) {
      return expected.codeColumnEndEffective == actual.codeColumnEndEffective
    }

    return true
  }

  private fun positionActualAssertions(
    originalText: String,
    expectedByCodeLine: Map<Int, List<PyTestAssertion>>,
    actualByCodeLine: Map<Int, List<PyTestAssertion>>,
    counterparts: Map<PyTestAssertion, PyTestAssertion>,
  ): String {
    val result = StringBuilder(originalText)

    val allCodeLines = expectedByCodeLine.keys + actualByCodeLine.keys
    val allCodeLinesSorted = allCodeLines.toSortedSet(compareByDescending { it })
    for (codeLine in allCodeLinesSorted) {
      val expectedAssertions = expectedByCodeLine[codeLine].orEmpty()
      val actualAssertions = actualByCodeLine[codeLine].orEmpty()
      val unmatchedAssertions = expectedAssertions - counterparts.values.toSet()

      val inlineActual = actualAssertions.filter { counterparts[it]?.isInlineAssertion() ?: false }.singleOrNull()
      val inlineExpected = inlineActual?.let { counterparts.getValue(it) } ?: unmatchedAssertions.singleOrNull { it.isInlineAssertion() }
      val commentExpected = expectedAssertions - listOfNotNull(inlineExpected)
      val commentActual = actualAssertions - listOfNotNull(inlineActual) +
                          unmatchedAssertions.filter { it !== inlineExpected && it.content.isBlank() && it.hasFixme }

      // The edits go from the end of the text to its start, so each edit keeps the offsets of the next one.
      // The comment assertions are below the code line, and the inline assertion is on it.
      removeAssertions(result, commentExpected)
      if (inlineExpected != null) removeAssertions(result, listOf(inlineExpected), removeSpaceBefore = inlineActual == null)
      insertCommentAssertions(result, commentExpected, commentActual)
      if (inlineActual != null) insertInlineAssertion(result, inlineExpected!!, inlineActual)
    }

    return result.toString()
  }

  /** Writes [actualAssertion] at the end of its code line. A multi-line payload goes above the comment assertions of the line. */
  private fun insertInlineAssertion(
    text: StringBuilder,
    expectedAssertion: PyTestAssertion,
    actualAssertion: PyTestAssertion,
  ) {
    val endOfLineOffset = findLineEndOffset(text, actualAssertion)
    val [inlineText, _] = actualAssertion.asInlineAssertion().asText(hashColumn = expectedAssertion.assertionColumnStart.coerceAtLeast(0))
    text.insert(endOfLineOffset, inlineText)
  }

  private fun insertCommentAssertions(
    text: StringBuilder,
    expectedAssertions: List<PyTestAssertion>,
    actualAndPlaceholderAssertions: List<PyTestAssertion>,
  ) {
    if (actualAndPlaceholderAssertions.isEmpty()) return

    val sortedActualAssertions = actualAndPlaceholderAssertions.sortedWith(compareBy(
      { -it.codeColumnStart },
      { it.type },
      { it.content },
      { it.fixmeContent ?: "" })
    )

    val hashColumn = commentHashColumn(expectedAssertions, actualAndPlaceholderAssertions)
    var serializedAssertions = emptyList<String>()
    for (actualAssertion in sortedActualAssertions) {
      val [newAssertion, serializedAssertionsUpdated] = actualAssertion.asText(serializedAssertions, hashColumn)
      serializedAssertions = serializedAssertionsUpdated + newAssertion
    }

    val endOfLineOffset = findLineEndOffset(text, actualAndPlaceholderAssertions.first())
    for (serializedAssertion in serializedAssertions.asReversed()) {
      text.insert(endOfLineOffset, "\n$serializedAssertion")
    }
  }

  /**
   * Returns the column of the `#` for the marker lines of one code line.
   *
   * It is the column of the expected marker lines when they all use one column and every actual marker can stand right of it.
   * Otherwise, it is 0.
   */
  private fun commentHashColumn(expectedAssertions: List<PyTestAssertion>, actualAssertions: List<PyTestAssertion>): Int {
    val column = expectedAssertions.filter { !it.isInlineAssertion() }.map { it.assertionColumnStart }.distinct().singleOrNull() ?: return 0
    if (column < 0) return 0
    return if (actualAssertions.all { it.isInlineAssertion() || it.codeColumnStart >= column }) column else 0
  }

  /** Removes the text of [assertions]. With [removeSpaceBefore], it also removes the spaces between an inline assertion and its code. */
  private fun removeAssertions(text: StringBuilder, assertions: List<PyTestAssertion>, removeSpaceBefore: Boolean = false) {
    if (assertions.isEmpty()) return
    val rangeStart = assertions.minOf { it.assertionOffsetStart }
    val rangeEnd = assertions.maxOf { it.assertionOffsetEnd }

    if (rangeStart !in 0..rangeEnd || rangeEnd > text.length) {
      return
    }

    var deleteStart = rangeStart
    var deleteEnd = rangeEnd

    val allInline = assertions.all { it.isInlineAssertion() }
    if (!allInline) {
      val lineStart = text.lastIndexOf(NEWLINE.toString(), deleteStart - 1) + 1
      if (text.substring(lineStart, deleteStart).isBlank()) {
        deleteStart = lineStart
      }
      if (deleteEnd < text.length && text[deleteEnd] == NEWLINE) {
        deleteEnd++
      }
      else if (deleteStart > 0 && text[deleteStart - 1] == NEWLINE) {
        deleteStart--
      }
    }
    else if (removeSpaceBefore) {
      while (deleteStart > 0 && text[deleteStart - 1] == ' ') deleteStart--
    }

    text.delete(deleteStart, deleteEnd)
  }

  private fun findLineEndOffset(text: StringBuilder, assertion: PyTestAssertion): Int {
    val codeOffsetStart = assertion.codeOffsetStart
    val endOfLineOffset = text.indexOf(NEWLINE, codeOffsetStart)
    if (endOfLineOffset < 0) {
      return text.length
    }
    return endOfLineOffset
  }
}
