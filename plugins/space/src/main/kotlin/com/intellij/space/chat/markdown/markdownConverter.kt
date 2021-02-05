// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.markdown

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.flavours.gfm.StrikeThroughParser
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.SimpleInlineTagProvider
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkdownParser
import org.intellij.markdown.parser.sequentialparsers.RangesListBuilder
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.intellij.markdown.parser.sequentialparsers.SequentialParserManager
import org.intellij.markdown.parser.sequentialparsers.TokensCache
import org.intellij.markdown.parser.sequentialparsers.impl.*
import org.jetbrains.annotations.Contract
import java.net.URI

@Contract(pure = true)
internal fun convertToHtml(markdownText: String, server: String? = null): String {
  val flavour = SpaceFlavourDescriptor()
  val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(markdownText)
  val providers = flavour.createHtmlGeneratingProviders(
    linkMap = LinkMap.buildLinkMap(parsedTree, markdownText),
    baseURI = server?.let { URI(it) }
  )
  val htmlText = HtmlGenerator(markdownText, parsedTree, providers, false).generateHtml()
  return htmlText.removePrefix("<body>").removeSuffix("</body>")
}

private class SpaceFlavourDescriptor : GFMFlavourDescriptor() {
  override val sequentialParserManager = object : SequentialParserManager() {
    override fun getParserSequence(): List<SequentialParser> = listOf(
      AutolinkParser(listOf(MarkdownTokenTypes.AUTOLINK, GFMTokenTypes.GFM_AUTOLINK)),
      BacktickParser(),
      ImageParser(),
      InlineLinkParser(),
      ReferenceLinkParser(),
      LineBreaksParser(), // difference with super
      StrikeThroughParser(),
      EmphStrongParser()
    )
  }

  override fun createHtmlGeneratingProviders(linkMap: LinkMap, baseURI: URI?): Map<IElementType, GeneratingProvider> {
    val parentProviders = super.createHtmlGeneratingProviders(linkMap, baseURI).toMutableMap()
    parentProviders[MarkdownElementTypes.EMPH] = SimpleInlineTagProvider("i", 1, -1)
    parentProviders[MarkdownElementTypes.STRONG] = SimpleInlineTagProvider("b", 2, -2)
    parentProviders[GFMElementTypes.STRIKETHROUGH] = SimpleInlineTagProvider("strike", 2, -2)
    return parentProviders
  }
}

private class LineBreaksParser : SequentialParser {
  override fun parse(tokens: TokensCache, rangesToGlue: List<IntRange>): SequentialParser.ParsingResult {
    val result = SequentialParser.ParsingResultBuilder()
    val delegateIndices = RangesListBuilder()
    var iterator = tokens.RangesListIterator(rangesToGlue)

    while (iterator.type != null) {
      if (iterator.type == MarkdownTokenTypes.EOL) {
        result.withNode(SequentialParser.Node(iterator.index..iterator.index + 1, MarkdownTokenTypes.HARD_LINE_BREAK))
      }
      else {
        delegateIndices.put(iterator.index)
      }

      iterator = iterator.advance()
    }

    return result.withFurtherProcessing(delegateIndices.get())
  }
}