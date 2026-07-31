// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.parser.at

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.lexer.GeneratedLexer
import org.intellij.markdown.lexer.MarkdownLexer
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MarkdownAtPathLexer(private val delegate: MarkdownLexer) : GeneratedLexer {
  private var segments: List<Segment> = emptyList()
  private var nextSegmentIndex = 0

  override var tokenStart: Int = 0
    private set

  override var tokenEnd: Int = 0
    private set

  override val state: Int
    get() = if (nextSegmentIndex > 0 && segments[nextSegmentIndex - 1].type == MarkdownAtPathElementTypes.PATH_TOKEN) 1 else delegate.state

  override fun reset(buffer: CharSequence, start: Int, end: Int, initialState: Int) {
    delegate.start(buffer, start, end, initialState)
    segments = splitCurrentToken()
    nextSegmentIndex = 0
  }

  override fun advance(): IElementType? {
    while (nextSegmentIndex >= segments.size) {
      if (!delegate.advance()) return null
      segments = splitCurrentToken()
      nextSegmentIndex = 0
    }
    val segment = segments[nextSegmentIndex++]
    tokenStart = segment.start
    tokenEnd = segment.end
    return segment.type
  }

  private fun splitCurrentToken(): List<Segment> {
    val type = delegate.type ?: return emptyList()
    if (type != MarkdownTokenTypes.TEXT) return listOf(Segment(type, delegate.tokenStart, delegate.tokenEnd))
    if (delegate.originalText.getOrNull(delegate.tokenStart - 1) == '[') {
      return listOf(Segment(type, delegate.tokenStart, delegate.tokenEnd))
    }

    val tokenText = delegate.originalText.subSequence(delegate.tokenStart, delegate.tokenEnd)
    val matches = PATH.findAll(tokenText).toList()
    if (matches.isEmpty()) return listOf(Segment(type, delegate.tokenStart, delegate.tokenEnd))

    return buildList {
      var offset = 0
      for (match in matches) {
        if (offset < match.range.first) {
          add(Segment(type, delegate.tokenStart + offset, delegate.tokenStart + match.range.first))
        }
        add(Segment(MarkdownAtPathElementTypes.PATH_TOKEN, delegate.tokenStart + match.range.first, delegate.tokenStart + match.range.last + 1))
        offset = match.range.last + 1
      }
      if (offset < tokenText.length) {
        add(Segment(type, delegate.tokenStart + offset, delegate.tokenEnd))
      }
    }
  }

  private data class Segment(val type: IElementType, val start: Int, val end: Int)

  companion object {
    private val PATH = Regex("(?<![\\w@])@[\\w./-]*")
  }
}
