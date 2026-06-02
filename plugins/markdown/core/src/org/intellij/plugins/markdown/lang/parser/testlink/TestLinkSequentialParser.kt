// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.parser.testlink

import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.parser.sequentialparsers.RangesListBuilder
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.intellij.markdown.parser.sequentialparsers.TokensCache

internal class TestLinkSequentialParser : SequentialParser {
  override fun parse(tokens: TokensCache, rangesToGlue: List<IntRange>): SequentialParser.ParsingResult {
    val result = SequentialParser.ParsingResultBuilder()
    val delegate = RangesListBuilder()
    var iterator: TokensCache.Iterator = tokens.RangesListIterator(rangesToGlue)

    while (iterator.type != null) {
      val path = findPath(tokens, iterator)
      if (path != null) {
        var head: TokensCache.Iterator = iterator
        while (head.index < path.index) {
          delegate.put(head.index)
          head = head.advance()
        }
        result.withNode(SequentialParser.Node(path.index..path.index + 1, TestLinkElementTypes.LINK))
        iterator = path.advance()
        continue
      }
      delegate.put(iterator.index)
      iterator = iterator.advance()
    }
    return result.withFurtherProcessing(delegate.get())
  }

  private fun findPath(tokens: TokensCache, start: TokensCache.Iterator): TokensCache.Iterator? {
    if (start.type != MarkdownTokenTypes.LBRACKET) return null

    val label = start.advance()
    if (label.type != MarkdownTokenTypes.TEXT) return null
    val labelText = tokens.originalText.subSequence(label.start, label.end).toString()
    if (labelText != TestLinkElementTypes.LABEL_NAME) return null

    val rbracket = label.advance()
    if (rbracket.type != MarkdownTokenTypes.RBRACKET) return null

    val path = rbracket.advance()
    if (path.type != MarkdownTokenTypes.TEXT) return null
    return path
  }
}
