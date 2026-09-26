// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.parser.at

import org.intellij.markdown.parser.sequentialparsers.RangesListBuilder
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.intellij.markdown.parser.sequentialparsers.TokensCache

internal class MarkdownAtPathSequentialParser : SequentialParser {
  override fun parse(tokens: TokensCache, rangesToGlue: List<IntRange>): SequentialParser.ParsingResult {
    val result = SequentialParser.ParsingResultBuilder()
    val delegate = RangesListBuilder()
    var iterator: TokensCache.Iterator = tokens.RangesListIterator(rangesToGlue)

    while (iterator.type != null) {
      if (iterator.type == MarkdownAtPathElementTypes.PATH_TOKEN) {
        result.withNode(SequentialParser.Node(iterator.index..iterator.index + 1, MarkdownAtPathElementTypes.PATH))
      }
      else {
        delegate.put(iterator.index)
      }
      iterator = iterator.advance()
    }
    return result.withFurtherProcessing(delegate.get())
  }
}
