// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation.doctest

import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.ApiStatus

/**
 * Finds the doctest examples in the text of a docstring or a ReST line.
 *
 * An example starts at a `>>>` line. The next `>>>` and `...` lines extend it.
 * A line that a backslash joins to the line before it is not a new line in the value of the text.
 * Such a line extends an open example and never starts an example.
 * Each other line closes the example.
 */
@ApiStatus.Internal
object PyDoctestExamples {
  /**
   * Returns one range for each example. The ranges do not overlap.
   *
   * @param lines the lines of the host text, each with its line separator
   * @param endOffset gives the end of the example text on a line, from the start offset of the line and the line
   * @param isCodeBlockLine true for a line in the content of a code block. Such a line closes the example.
   * @param backslashJoinsLines true when a backslash at the given offset and the newline after it are an escape that joins
   * two lines. This is true in a non-raw string literal.
   */
  @JvmStatic
  @JvmOverloads
  fun findRanges(
    lines: List<String>,
    endOffset: (lineStart: Int, line: String) -> Int,
    isCodeBlockLine: (lineStart: Int) -> Boolean = { false },
    backslashJoinsLines: (offset: Int) -> Boolean = { false },
  ): List<TextRange> {
    val ranges = ArrayList<TextRange>()
    var start = -1
    var end = 0

    fun close() {
      if (start in 0 until end) {
        ranges.add(TextRange.create(start, end))
      }
      start = -1
    }

    var lineStart = 0
    var joinedToPrevious = false
    for (line in lines) {
      val joined = joinedToPrevious
      val content = line.removeSuffix("\n")
      joinedToPrevious = endsWithEscapeBackslash(content) && backslashJoinsLines(lineStart + content.length - 1)
      if (isCodeBlockLine(lineStart)) {
        close()
      }
      else if (joined) {
        if (start >= 0) {
          end = endOffset(lineStart, line)
        }
      }
      else {
        val trimmed = content.trim()
        if (trimmed.startsWith(">>>") || (start >= 0 && trimmed.startsWith("..."))) {
          if (start < 0) {
            start = lineStart
          }
          end = endOffset(lineStart, line)
        }
        else {
          close()
        }
      }
      lineStart += line.length
    }
    close()
    return ranges
  }

  private fun endsWithEscapeBackslash(content: String): Boolean = (content.length - content.trimEnd('\\').length) % 2 == 1
}
