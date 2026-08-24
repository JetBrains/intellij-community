// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor

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
    addRule(Rule(' ', WrapCondition.AFTER))
    addRule(Rule('\t', WrapCondition.AFTER))

    // Punctuation.
    addRule(Rule(',', WrapCondition.AFTER))
    addRule(Rule('.', WrapCondition.AFTER))
    addRule(Rule('!', WrapCondition.AFTER))
    addRule(Rule('?', WrapCondition.AFTER))
    addRule(Rule(';', WrapCondition.AFTER))

    // Brackets to wrap after.
    addRule(Rule(')', WrapCondition.AFTER))
    addRule(Rule(']', WrapCondition.AFTER))
    addRule(Rule('}', WrapCondition.AFTER))

    // Brackets to wrap before
    addRule(Rule('(', WrapCondition.BEFORE))
    addRule(Rule('[', WrapCondition.BEFORE))
    addRule(Rule('{', WrapCondition.BEFORE))
  }

  override fun calculateWrapPosition(editor: Editor, startOffset: Int, endOffset: Int,
                                     maxPreferredOffset: Int, allowToBeyondMaxPreferredOffset: Boolean,
                                     isSoftWrap: Boolean): Int {
    val document = getWrapDocument(editor, isSoftWrap)
    val position = super.calculateWrapPosition(document, editor.project, startOffset, endOffset, maxPreferredOffset,
                                               allowToBeyondMaxPreferredOffset, isSoftWrap)
    if (position < 0) return position
    val highlighter = editor.highlighter as? LexerEditorHighlighter ?: return if (isSoftWrap) position else -1
    val forbiddenStart = classifyForbidden(highlighter, document, position) ?: return position
    if (forbiddenStart == NEVER_WRAP || forbiddenStart <= startOffset) return -1
    val retry = super.calculateWrapPosition(document, editor.project, startOffset, forbiddenStart,
                                            minOf(maxPreferredOffset, forbiddenStart),
                                            allowToBeyondMaxPreferredOffset, isSoftWrap)
    return if (retry > 0) retry else forbiddenStart
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
   * Returns the offset of the construct the wrap has to be moved in front of, [NEVER_WRAP] when no position on this
   * line is acceptable, or null when wrapping at [position] is fine.
   */
  private fun classifyForbidden(highlighter: LexerEditorHighlighter, document: Document, position: Int): Int? {
    val iterator = highlighter.createIterator(position)
    if (iterator.atEnd()) return null
    val type = iterator.tokenType ?: return null

    // A table row is one CELL token per cell, links inside included, so every position in it is inside the table.
    if (type in TABLE_TOKENS) return NEVER_WRAP

    val line = document.getLineNumber(position)
    if (isSetextHeaderContent(highlighter, document, line)) return NEVER_WRAP

    // The ATX marker and the `[` that opens a link are both found by one walk back over the line. The marker has to be
    // looked for whatever the token at [position] is, since the content of a header is plain text.
    var linkStart = NOT_FOUND
    val lineStart = maxOf(document.getLineStartOffset(line), position - MAX_SCAN_DISTANCE)
    val tokens = highlighter.createIterator(position)
    while (!tokens.atEnd() && tokens.start >= lineStart) {
      val walked = tokens.tokenType
      if (walked == MarkdownTokenTypes.ATX_HEADER) return NEVER_WRAP
      if (walked == MarkdownTokenTypes.LBRACKET && linkStart == NOT_FOUND) linkStart = tokens.start
      if (tokens.start == 0) break
      tokens.retreat()
    }

    // Inside a destination, an autolink or a title the wrap belongs in front of the whole link.
    if (type in FORBIDDEN_INSIDE_LINK) return if (linkStart == NOT_FOUND) iterator.start else linkStart
    // Ambiguous brackets/parens: forbidden only as part of a `[…](…)` sequence, and a wrap exactly at the opening
    // `[` is allowed, since that already wraps in front of the link.
    if (type !in LINK_BRACKETS || linkStart == NOT_FOUND ||
        (type == MarkdownTokenTypes.LBRACKET && iterator.start == position)) {
      return null
    }
    return if (hasClosingParen(highlighter, position, document.getLineEndOffset(line))) linkStart else null
  }

  private fun isSetextHeaderContent(highlighter: LexerEditorHighlighter, document: Document, line: Int): Boolean {
    if (line + 1 >= document.lineCount) return false
    val iterator = highlighter.createIterator(document.getLineStartOffset(line + 1))
    return !iterator.atEnd() && iterator.tokenType in SETEXT_UNDERLINES
  }

  private fun hasClosingParen(highlighter: LexerEditorHighlighter, position: Int, lineEnd: Int): Boolean {
    val end = minOf(lineEnd, position + MAX_SCAN_DISTANCE)
    val tokens = highlighter.createIterator(position)
    while (!tokens.atEnd() && tokens.start < end) {
      if (tokens.tokenType == MarkdownTokenTypes.RPAREN) return true
      tokens.advance()
    }
    return false
  }
}

private const val NEVER_WRAP = -1
private const val NOT_FOUND = -1
private const val MAX_SCAN_DISTANCE = 4096

private val TABLE_TOKENS = setOf(MarkdownElementTypes.TABLE_CELL, MarkdownTokenTypes.TABLE_SEPARATOR)

private val FORBIDDEN_INSIDE_LINK = setOf(
  MarkdownTokenTypes.URL,
  MarkdownTokenTypes.AUTOLINK,
  MarkdownTokenTypes.GFM_AUTOLINK,
  MarkdownTokenTypes.EMAIL_AUTOLINK,
  MarkdownTokenTypes.LINK_TITLE,
)

private val LINK_BRACKETS = setOf(
  MarkdownTokenTypes.LBRACKET,
  MarkdownTokenTypes.RBRACKET,
  MarkdownTokenTypes.LPAREN,
  MarkdownTokenTypes.RPAREN,
)

private val SETEXT_UNDERLINES = setOf(
  MarkdownTokenTypes.SETEXT_1,
  MarkdownTokenTypes.SETEXT_2,
)
