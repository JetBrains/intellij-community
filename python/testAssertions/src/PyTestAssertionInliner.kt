// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.testAssertions

import com.intellij.python.testAssertions.PyTestAssertionSyntax.NEWLINE
import kotlin.math.abs

/**
 * Writes actual assertions into a test text in place of the expected ones.
 *
 * When the actual assertions equal the expected ones, the result equals the original text. So a test compares two texts,
 * and a failure shows the full actual text.
 */
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

      val inlineActualAssertions = actualAssertions.filter { counterparts[it]?.isInlineAssertion() ?: false }
      val inlineUnmatchedExpectedAssertions = unmatchedAssertions.filter { it.isInlineAssertion() }
      if (inlineActualAssertions.size == 1) {
        val inlineActual = inlineActualAssertions.single()
        val inlineExpected = counterparts[inlineActual]!!
        val commentActuals = actualAssertions - inlineActual
        val commentExpected = expectedAssertions - inlineExpected
        replaceCommentAssertion(result, commentExpected, commentActuals)
        replaceInlineAssertion(result, inlineExpected, inlineActual)
      }
      else if (inlineUnmatchedExpectedAssertions.size == 1) {
        removeAssertions(result, inlineUnmatchedExpectedAssertions)
      }
      else {
        val actualAndPlaceholderAssertions = actualAssertions + unmatchedAssertions.filter { it.content.isBlank() && it.hasFixme }
        replaceCommentAssertion(result, expectedAssertions, actualAndPlaceholderAssertions)
      }
    }

    return result.toString()
  }

  private fun replaceInlineAssertion(
    text: StringBuilder,
    expectedAssertion: PyTestAssertion,
    actualAssertion: PyTestAssertion,
  ) {

    removeAssertions(text, listOf(expectedAssertion))
    val endOfLineOffset = findLineEndOffset(text, actualAssertion)
    val (inlineText, _) = actualAssertion.asInlineAssertion().asText()
    text.insert(endOfLineOffset, inlineText)
  }

  private fun replaceCommentAssertion(
    text: StringBuilder,
    expectedAssertions: List<PyTestAssertion>,
    actualAndPlaceholderAssertions: List<PyTestAssertion>,
  ) {
    removeAssertions(text, expectedAssertions)
    if (actualAndPlaceholderAssertions.isEmpty()) return

    val sortedActualAssertions = actualAndPlaceholderAssertions.sortedWith(compareBy(
      { -it.codeColumnStart },
      { it.type },
      { it.content },
      { it.fixmeContent ?: "" })
    )

    var serializedAssertions = emptyList<String>()
    for (actualAssertion in sortedActualAssertions) {
      val (newAssertion, serializedAssertionsUpdated) = actualAssertion.asText(serializedAssertions)
      serializedAssertions = serializedAssertionsUpdated + newAssertion
    }

    val endOfLineOffset = findLineEndOffset(text, actualAndPlaceholderAssertions.first())
    for (serializedAssertion in serializedAssertions.asReversed()) {
      text.insert(endOfLineOffset, "\n$serializedAssertion")
    }
  }

  private fun removeAssertions(text: StringBuilder, assertions: List<PyTestAssertion>) {
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
      if (deleteEnd < text.length && text[deleteEnd] == NEWLINE) {
        deleteEnd++
      }
      else if (deleteStart > 0 && text[deleteStart - 1] == NEWLINE) {
        deleteStart--
      }
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
