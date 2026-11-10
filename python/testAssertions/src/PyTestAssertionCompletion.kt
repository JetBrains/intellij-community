// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.testAssertions

import com.intellij.python.testAssertions.PyTestAssertionSyntax.COMMENT_CHAR
import com.intellij.python.testAssertions.PyTestAssertionSyntax.MARKER_CORNER
import com.intellij.python.testAssertions.PyTestAssertionSyntax.MARKER_LEFT
import com.intellij.python.testAssertions.PyTestAssertionSyntax.indexOfUnescapedCommentChar
import com.intellij.python.testAssertions.PyTestAssertionSyntax.isAssertionMarker
import org.jetbrains.annotations.ApiStatus

/** One completion item for a test text. */
@ApiStatus.Internal
data class PyTestCompletionItem(val text: String, val tailText: String, val typeText: String)

/**
 * The completion items at the caret, and the prefix that they replace.
 *
 * [prefix] is the part of the current word before the caret.
 */
@ApiStatus.Internal
data class PyTestCompletion(val prefix: String, val items: List<PyTestCompletionItem>)

/**
 * Computes the completion of the assertion language from the text of the line before the caret.
 *
 * It reads the start of the assertion as the parser does. The English texts of the items are DevKit texts for the developers of
 * the IntelliJ Platform, and this library has no message bundle. So they are not in a `*.properties` file.
 */
@ApiStatus.Internal
object PyTestAssertionCompletion {

  /** Returns the completion for [linePrefix], or null when the caret is not in an assertion comment. */
  fun complete(linePrefix: String): PyTestCompletion? {
    var head = PyTestAssertionHead.scan(linePrefix) ?: return null
    val wordPrefix = linePrefix.substring(linePrefix.indexOfLast { it.isWhitespace() || isAssertionMarker(it) || it == COMMENT_CHAR } + 1)

    if (head.isCommentLine && head.marker == null) {
      val afterHash = linePrefix.substring(head.hashColumn + 1)
      return when {
        afterHash.isEmpty() -> PyTestCompletion("", markerItems(includeLeftMarker = true))
        afterHash.isBlank() -> PyTestCompletion("", markerItems(includeLeftMarker = false))
        else -> null
      }
    }
    if (!head.isCommentLine && !isDirective(head)) {
      // The caret is in a new comment after the `#` that the parser finds, for example after a `#` in a string literal.
      head = PyTestAssertionHead.scan(linePrefix, linePrefix.lastIndexOf(COMMENT_CHAR)) ?: return null
    }

    val name = head.name ?: return null
    if (name.end == linePrefix.length) return PyTestCompletion(wordPrefix, assertionItems())
    val directive = PyTestDirectives.findAssertion(head.nameText) ?: return null
    return afterDirective(linePrefix, name.end, directive, wordPrefix)
  }

  /** Returns true when a known directive follows the `#` of [head], and the caret is right of it. */
  private fun isDirective(head: PyTestAssertionHead): Boolean {
    val name = head.name ?: return false
    return name.end < head.line.length && PyTestDirectives.findAssertion(head.nameText) != null
  }

  private fun afterDirective(linePrefix: String, nameEnd: Int, directive: PyTestDirective, wordPrefix: String): PyTestCompletion? {
    if (indexOfUnescapedCommentChar(linePrefix, nameEnd) >= 0) return null
    val payloadWord = PyTestLineCursor(linePrefix, nameEnd).skipSpaces().word()
    val isFirstPayloadWord = payloadWord.end == linePrefix.length
    val items = buildList {
      if (isFirstPayloadWord) {
        directive.values.mapTo(this) { PyTestCompletionItem(it, "", "value") }
        if (directive.isSeverity) add(item(PyTestDirectives.TOOLTIP))
      }
      add(item(PyTestDirectives.FIXME))
    }
    return PyTestCompletion(wordPrefix, items)
  }

  private fun assertionItems(): List<PyTestCompletionItem> =
    PyTestDirectives.all.filter { it.kind == PyTestDirectiveKind.ASSERTION }.map(::item)

  private fun markerItems(includeLeftMarker: Boolean): List<PyTestCompletionItem> = buildList {
    add(PyTestCompletionItem(MARKER_CORNER.toString(), " marks the column of the code line above", "marker"))
    if (includeLeftMarker) add(PyTestCompletionItem(MARKER_LEFT.toString(), " marks the column of the # in the code line above", "marker"))
  }

  private fun item(directive: PyTestDirective): PyTestCompletionItem {
    val tail = if (directive.payloadGrammar.isEmpty()) "" else " ${directive.payloadGrammar}"
    return PyTestCompletionItem(directive.name, tail, directive.kind.name.lowercase())
  }
}
