// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.GenericLineWrapPositionStrategy
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import org.intellij.plugins.markdown.editor.tables.ui.alignment.isMarkdownTableVisualAlignmentEnabled
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes

class MarkdownLineWrapPositionStrategy : GenericLineWrapPositionStrategy() {
  init {
    // We should wrap after space, cause otherwise formatting will eat space once AutoWrapHandler made wrap
    WRAP_AFTER_CHARS.forEach { addRule(Rule(it, WrapCondition.AFTER)) }
  }

  /**
   * Allows wrapping only after a space or tab, including virtual wraps.
   */
  override fun canUseOffset(document: Document, offset: Int, virtual: Boolean): Boolean {
    // Keep this check in sync with WRAP_AFTER_CHARS, which defines the rules above.
    val character = document.charsSequence[offset]
    return character == ' ' || character == '\t'
  }

  override fun calculateWrapPosition(editor: Editor, startOffset: Int, endOffset: Int,
                                     maxPreferredOffset: Int, allowToBeyondMaxPreferredOffset: Boolean,
                                     isSoftWrap: Boolean): Int {
    val document = getWrapDocument(editor, isSoftWrap)
    val position = super.calculateWrapPosition(document, editor.project, startOffset, endOffset, maxPreferredOffset,
                                               allowToBeyondMaxPreferredOffset, isSoftWrap)
    if (position < 0) return position
    val highlighter = editor.highlighter as? LexerEditorHighlighter ?: return if (isSoftWrap) position else -1
    return if (isWrapForbidden(highlighter, document, position)) -1 else position
  }

  override fun isSoftWrappingAllowed(editor: Editor, offset: Int): Boolean {
    val highlighter = editor.highlighter as? LexerEditorHighlighter ?: return true
    val document = editor.elfDocument
    if (document.textLength == 0) return true
    val line = document.getLineNumber(offset.coerceAtMost(document.textLength - 1))
    val lineStart = document.getLineStartOffset(line)
    val lineEnd = document.getLineEndOffset(line)
    val lookupEnd = minOf(lineEnd, lineStart + MAX_SCAN_DISTANCE)
    val tokens = highlighter.createIterator(lineStart)
    while (!tokens.atEnd() && tokens.start < lookupEnd) {
      if (tokens.tokenType in TABLE_TOKENS) return !isMarkdownTableVisualAlignmentEnabled(editor)
      tokens.advance()
    }
    return true
  }

  /**
   * Returns true when no position on the line of [position] is acceptable, that is, in a table row or a header.
   */
  private fun isWrapForbidden(highlighter: LexerEditorHighlighter, document: Document, position: Int): Boolean {
    val iterator = highlighter.createIterator(position)
    if (iterator.atEnd()) return false

    // A table row is one CELL token per cell, links inside included, so every position in it is inside the table.
    if (iterator.tokenType in TABLE_TOKENS) return true

    val line = document.getLineNumber(position)
    if (isSetextHeaderContent(highlighter, document, line)) return true

    // The content of a header is plain text, so the walk back looks for the ATX marker whatever the token at [position] is.
    val lineStart = maxOf(document.getLineStartOffset(line), position - MAX_SCAN_DISTANCE)
    val tokens = highlighter.createIterator(position)
    while (!tokens.atEnd() && tokens.start >= lineStart) {
      if (tokens.tokenType == MarkdownTokenTypes.ATX_HEADER) return true
      if (tokens.start == 0) break
      tokens.retreat()
    }
    return false
  }

  private fun isSetextHeaderContent(highlighter: LexerEditorHighlighter, document: Document, line: Int): Boolean {
    if (line + 1 >= document.lineCount) return false
    val iterator = highlighter.createIterator(document.getLineStartOffset(line + 1))
    return !iterator.atEnd() && iterator.tokenType in SETEXT_UNDERLINES
  }
}

private val WRAP_AFTER_CHARS = charArrayOf(' ', '\t')

private const val MAX_SCAN_DISTANCE = 4096

private val TABLE_TOKENS = setOf(MarkdownElementTypes.TABLE_CELL, MarkdownTokenTypes.TABLE_SEPARATOR)

private val SETEXT_UNDERLINES = setOf(
  MarkdownTokenTypes.SETEXT_1,
  MarkdownTokenTypes.SETEXT_2,
)
