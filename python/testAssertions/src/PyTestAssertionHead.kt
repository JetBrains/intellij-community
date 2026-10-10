// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.testAssertions

import com.intellij.python.testAssertions.PyTestAssertionSyntax.COMMENT_CHAR
import com.intellij.python.testAssertions.PyTestAssertionSyntax.isAssertionMarker
import com.intellij.python.testAssertions.PyTestAssertionSyntax.isGuide

/**
 * Reads one line of a test text from left to right.
 *
 * [column] is the read position. A column starts at 0. A range that a method returns holds columns.
 */
internal class PyTestLineCursor(private val line: String, var column: Int = 0) {

  fun peek(): Char? = line.getOrNull(column)

  fun skipWhile(predicate: (Char) -> Boolean): PyTestLineCursor {
    while (column < line.length && predicate(line[column])) column++
    return this
  }

  fun skipSpaces(): PyTestLineCursor = skipWhile(Char::isWhitespace)

  /** Reads the characters that match [predicate] and returns their columns. The range is empty when no character matches. */
  fun take(predicate: (Char) -> Boolean): IntRange {
    val start = column
    skipWhile(predicate)
    return start until column
  }

  fun word(): IntRange = take { !it.isWhitespace() }
}

/** The exclusive end of a range of columns or offsets. The stdlib `IntRange.endExclusive` is deprecated. */
internal val IntRange.end: Int get() = last + 1

internal val IntRange.length: Int get() = end - first

/**
 * The start of a possible assertion on one line: the `#`, the marker and the directive name.
 *
 * The ranges hold columns of [line]. [name] is the word after the marker, or after the `#` of a code line.
 * It is empty at the end of the line.
 * [name] is null when no assertion can start after this `#`: on a comment line without a marker, and on a code line with a marker.
 */
internal class PyTestAssertionHead private constructor(
  val line: String,
  val hashColumn: Int,
  /** True when the line holds only a comment. Such a line needs a marker to start an assertion. */
  val isCommentLine: Boolean,
  val marker: IntRange?,
  val name: IntRange?,
) {
  val nameText: String get() = name?.let { line.substring(it) }.orEmpty()

  /**
   * True when this comment line starts an assertion: it has a marker and a name that starts with a letter.
   *
   * So a payload line such as `# \#x` or `# ^ 1` continues the assertion above it.
   */
  val startsMarkerAssertion: Boolean get() = marker != null && name != null && !name.isEmpty() && line[name.first].isLetter()

  companion object {
    /** Returns the head after the `#` in [hashColumn] of [line], or null when [line] has no `#`. */
    fun scan(line: String, hashColumn: Int = findAssertionHashIndex(line)): PyTestAssertionHead? {
      if (hashColumn < 0) return null
      val isCommentLine = line.substring(0, hashColumn).isBlank()
      val cursor = PyTestLineCursor(line, hashColumn + 1)
      var marker: IntRange? = null
      if (isCommentLine) {
        cursor.skipWhile { it.isWhitespace() || isGuide(it) }
        marker = cursor.take(::isAssertionMarker)
        if (marker.isEmpty()) return PyTestAssertionHead(line, hashColumn, isCommentLine = true, marker = null, name = null)
      }
      cursor.skipSpaces()
      if (!isCommentLine && cursor.peek()?.let(::isAssertionMarker) == true) {
        return PyTestAssertionHead(line, hashColumn, isCommentLine = false, marker = null, name = null)
      }
      return PyTestAssertionHead(line, hashColumn, isCommentLine, marker, cursor.word())
    }

    /**
     * Returns the index of the `#` that can start an assertion in [line], or -1.
     *
     * On a code line, it is the first `#` that a directive follows. So a `#` in a string literal before an inline assertion
     * does not hide the assertion. Without such a `#`, it is the first `#`.
     */
    fun findAssertionHashIndex(line: String): Int {
      val first = line.indexOf(COMMENT_CHAR)
      if (first < 0 || line.substring(0, first).isBlank()) return first
      var idx = first
      while (idx >= 0) {
        val word = line.substring(PyTestLineCursor(line, idx + 1).skipSpaces().word())
        if (PyTestDirectives.findAssertion(word) != null || PyTestDirectives.findNearMiss(word) != null) return idx
        idx = line.indexOf(COMMENT_CHAR, idx + 1)
      }
      return first
    }
  }
}
