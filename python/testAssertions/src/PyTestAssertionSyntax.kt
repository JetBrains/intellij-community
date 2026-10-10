// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.testAssertions

/** The characters of the assertion mini-language and the escape of its payload. */
object PyTestAssertionSyntax {
  const val COMMENT_CHAR: Char = '#'
  const val MARKER_CORNER: Char = '└'
  const val MARKER_LEFT: Char = '\\'
  const val MARKER_SPAN: Char = '^'
  const val GUIDE_BAR: Char = '│'
  const val GUIDE_BAR_ASCII: Char = '|'
  const val NEWLINE: Char = '\n'

  internal fun isGuide(ch: Char): Boolean = ch == GUIDE_BAR || ch == GUIDE_BAR_ASCII

  /** Returns true for a character of a marker. A span can hold a [GUIDE_BAR] where the guide of a marker line below crosses it. */
  internal fun isAssertionMarker(ch: Char): Boolean = ch == MARKER_CORNER || ch == MARKER_LEFT || ch == MARKER_SPAN || ch == GUIDE_BAR

  /**
   * The escape of a `#` inside a payload. Without it, a `#` starts the trailing comment of the assertion.
   *
   * The escape has no escape of its own. So a payload cannot end with a `\` right before the trailing comment.
   */
  const val ESCAPED_COMMENT_CHAR: String = "\\#"

  fun escape(payload: String): String = payload.replace(COMMENT_CHAR.toString(), ESCAPED_COMMENT_CHAR)

  fun unescape(payload: String): String = payload.replace(ESCAPED_COMMENT_CHAR, COMMENT_CHAR.toString())

  /**
   * Returns [payload] as a test text holds it: escaped, and with each further line on a new comment line.
   *
   * [hashColumn] is the column of the `#` of the further lines. An empty line is a `#` alone.
   * A further line must not start with a marker and a word that starts with a letter, because that is the start of a new assertion.
   */
  fun renderPayload(payload: String, hashColumn: Int): String =
    escape(payload).split(NEWLINE).withIndex().joinToString("") { (index, line) ->
      if (index == 0) line else "$NEWLINE${continuationPrefix(hashColumn, line)}$line"
    }

  /** Returns the start of a further payload line: the `#` in [hashColumn], and a space when [line] is not empty. */
  internal fun continuationPrefix(hashColumn: Int, line: String): String =
    " ".repeat(hashColumn) + COMMENT_CHAR + if (line.isEmpty()) "" else " "

  /** Returns the index of the first `#` in [text] at or after [startIndex] that is not escaped, or -1. */
  internal fun indexOfUnescapedCommentChar(text: String, startIndex: Int = 0): Int {
    var idx = text.indexOf(COMMENT_CHAR, startIndex)
    while (idx > 0 && text[idx - 1] == '\\') {
      idx = text.indexOf(COMMENT_CHAR, idx + 1)
    }
    return idx
  }
}
