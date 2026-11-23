// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.GenericLineWrapPositionStrategy
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import org.intellij.plugins.markdown.editor.tables.ui.alignment.isMarkdownTableVisualAlignmentEnabled
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
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
    var wrapPosition = position
    while (!isSoftWrap && isWrapAfterListPrefix(highlighter, document, wrapPosition)) {
      val nextPosition = super.calculateWrapPosition(document, editor.project, wrapPosition + 1, endOffset,
                                                     maxPreferredOffset, true, isSoftWrap)
      if (nextPosition <= wrapPosition) return -1
      wrapPosition = nextPosition
    }
    return if (isWrapForbidden(highlighter, document, wrapPosition)) -1 else wrapPosition
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

  /** Returns true when wrapping at [position] would break Markdown structure. */
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

  private fun isWrapAfterListPrefix(highlighter: LexerEditorHighlighter, document: Document, position: Int): Boolean {
    if (position == 0) return false
    val lineStart = document.getLineStartOffset(document.getLineNumber(position - 1))
    val iterator = highlighter.createIterator(position - 1)
    while (!iterator.atEnd() && iterator.start >= lineStart && iterator.tokenType in MarkdownTokenTypeSets.WHITE_SPACES) {
      iterator.retreat()
    }
    if (!iterator.atEnd() && iterator.tokenType == MarkdownTokenTypes.CHECK_BOX) {
      iterator.retreat()
      while (!iterator.atEnd() && iterator.start >= lineStart && iterator.tokenType in MarkdownTokenTypeSets.WHITE_SPACES) {
        iterator.retreat()
      }
    }
    return !iterator.atEnd() && iterator.start >= lineStart && iterator.tokenType in MarkdownTokenTypeSets.LIST_MARKERS
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
