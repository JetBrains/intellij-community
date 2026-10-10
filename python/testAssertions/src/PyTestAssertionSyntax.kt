// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.testAssertions

/** The characters of the assertion mini-language and the scanning helpers of its parser. */
object PyTestAssertionSyntax {
  const val COMMENT_CHAR: Char = '#'
  const val MARKER_CORNER: Char = '└'
  const val MARKER_LEFT: Char = '\\'
  const val MARKER_SPAN: Char = '^'
  const val GUIDE_BAR: Char = '│'
  const val GUIDE_BAR_ASCII: Char = '|'
  const val NEWLINE: Char = '\n'

  fun skipWhitespace(text: String, startIndex: Int): Int {
    var i = startIndex
    while (i < text.length && text[i].isWhitespace()) {
      i++
    }
    return i
  }

  fun skipWhitespaceAndGuides(text: String): Int {
    var i = 0
    while (i < text.length && (text[i].isWhitespace() || isGuide(text[i]))) {
      i++
    }
    return i
  }

  fun isGuide(ch: Char): Boolean = ch == GUIDE_BAR || ch == GUIDE_BAR_ASCII

  fun scanTokenEnd(text: String, startIndex: Int): Int {
    var i = startIndex
    while (i < text.length && !text[i].isWhitespace()) {
      i++
    }
    return i
  }

  fun isAssertionMarker(ch: Char): Boolean = ch == MARKER_CORNER || ch == MARKER_LEFT || ch == MARKER_SPAN || ch == GUIDE_BAR
}
