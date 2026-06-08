// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.GenericLineWrapPositionStrategy
import com.intellij.openapi.editor.colors.impl.EmptyColorScheme
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IElementType
import org.intellij.plugins.markdown.highlighting.MarkdownSyntaxHighlighter
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

  override fun calculateWrapPosition(document: Document, project: Project?, startOffset: Int, endOffset: Int, maxPreferredOffset: Int,
                                     allowToBeyondMaxPreferredOffset: Boolean, isSoftWrap: Boolean): Int {
    val position = super.calculateWrapPosition(document, project, startOffset, endOffset, maxPreferredOffset,
                                               allowToBeyondMaxPreferredOffset, isSoftWrap)
    if (position < 0) return position
    val highlighter = LexerEditorHighlighter(MarkdownSyntaxHighlighter(), EmptyColorScheme.getEmptyScheme())
    highlighter.setText(document.immutableCharSequence)

    val decision = classifyForbidden(highlighter, position) ?: return position
    if (decision == ForbiddenDecision.NoWrap) return -1
    val forbiddenStart = (decision as ForbiddenDecision.WrapBefore).offset
    if (forbiddenStart <= startOffset) return -1
    val retry = super.calculateWrapPosition(document, project, startOffset, forbiddenStart,
                                            minOf(maxPreferredOffset, forbiddenStart),
                                            allowToBeyondMaxPreferredOffset, isSoftWrap)
    return if (retry > 0) retry else forbiddenStart
  }

  private sealed interface ForbiddenDecision {
    /** Wrapping at the candidate position is forbidden and there is no acceptable retry — return -1. */
    object NoWrap : ForbiddenDecision
    /** Wrapping at the candidate position is forbidden; retry with end clamped to [offset] (the construct's start). */
    data class WrapBefore(val offset: Int) : ForbiddenDecision
  }

  private fun classifyForbidden(highlighter: LexerEditorHighlighter, position: Int): ForbiddenDecision? {
    val iter = highlighter.createIterator(position)
    if (iter.atEnd()) return null
    val type = iter.tokenType ?: return null

    // Headers and tables: leaf tokens at [position] are usually TEXT/WHITE_SPACE inside the construct, so
    // walk the current line for the construct-defining markers.
    if (isInsideHeader(highlighter, position) || isInsideTableRow(highlighter, position)) return ForbiddenDecision.NoWrap

    // Inside a link destination / autolink / link title: clear-cut, walk back to the LBRACKET that opens the link.
    if (type in FORBIDDEN_INSIDE_LINK) {
      val linkStart = findOnLine(highlighter, position, Direction.BACKWARD) { it == MarkdownTokenTypes.LBRACKET }
      return ForbiddenDecision.WrapBefore(linkStart ?: iter.start)
    }

    // Ambiguous brackets/parens: only forbidden when part of a `[…](…)` sequence on this line.
    // Boundary: wrap exactly at the opening `[` (iter.start == position) is allowed — we wrap BEFORE the link.
    if (type == MarkdownTokenTypes.LBRACKET && iter.start == position) return null
    if (type in LINK_BRACKETS) {
      if (!looksLikeInlineLink(highlighter, position)) return null
      val linkStart = findOnLine(highlighter, position, Direction.BACKWARD) { it == MarkdownTokenTypes.LBRACKET } ?: return null
      return ForbiddenDecision.WrapBefore(linkStart)
    }
    return null
  }

  private fun isInsideHeader(highlighter: LexerEditorHighlighter, position: Int): Boolean =
    findOnLine(highlighter, position, Direction.BACKWARD) { it in HEADER_TOKENS } != null

  private fun isInsideTableRow(highlighter: LexerEditorHighlighter, position: Int): Boolean {
    val isSeparator: (IElementType) -> Boolean = { it == MarkdownTokenTypes.TABLE_SEPARATOR }
    return findOnLine(highlighter, position, Direction.BACKWARD, isSeparator) != null
        || findOnLine(highlighter, position, Direction.FORWARD, isSeparator) != null
  }

  private fun looksLikeInlineLink(highlighter: LexerEditorHighlighter, position: Int): Boolean =
    findOnLine(highlighter, position, Direction.BACKWARD) { it == MarkdownTokenTypes.LBRACKET } != null &&
    findOnLine(highlighter, position, Direction.FORWARD) { it == MarkdownTokenTypes.RPAREN } != null

  /**
   * Walks the highlighter's token stream from [position] in [direction], stopping at EOL or the buffer ends.
   * Returns the start offset of the first token whose type matches [match], or null if none is found before
   * the line/buffer boundary.
   */
  private inline fun findOnLine(
    highlighter: LexerEditorHighlighter,
    position: Int,
    direction: Direction,
    match: (IElementType) -> Boolean,
  ): Int? {
    val it: HighlighterIterator = highlighter.createIterator(position)
    while (!it.atEnd()) {
      val t = it.tokenType
      if (t == MarkdownTokenTypes.EOL) return null
      if (t != null && match(t)) return it.start
      when (direction) {
        Direction.BACKWARD -> {
          if (it.start == 0) return null
          it.retreat()
        }
        Direction.FORWARD -> it.advance()
      }
    }
    return null
  }

  private enum class Direction { BACKWARD, FORWARD }

  companion object {
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
    private val HEADER_TOKENS = setOf(
      MarkdownTokenTypes.ATX_HEADER,
      MarkdownTokenTypes.ATX_CONTENT,
      MarkdownTokenTypes.SETEXT_1,
      MarkdownTokenTypes.SETEXT_2,
      MarkdownTokenTypes.SETEXT_CONTENT,
    )
  }
}
