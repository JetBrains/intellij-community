// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.parser

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.flavours.gfm.StrikeThroughDelimiterParser
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.SimpleInlineTagProvider
import org.intellij.markdown.html.TransparentInlineHolderProvider
import org.intellij.markdown.lexer.MarkdownLexer
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkerProcessorFactory
import org.intellij.markdown.parser.sequentialparsers.EmphasisLikeParser
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.intellij.markdown.parser.sequentialparsers.SequentialParserManager
import org.intellij.markdown.parser.sequentialparsers.impl.*
import org.intellij.plugins.markdown.lang.parser.frontmatter.FrontMatterHeaderMarkerProvider
import org.intellij.plugins.markdown.ui.preview.html.HeaderGeneratingProvider
import org.jetbrains.annotations.ApiStatus
import java.net.URI

@ApiStatus.Internal
class MarkdownDefaultFlavour(
  private val delegate: GFMFlavourDescriptor = GFMFlavourDescriptor()
): CommonMarkFlavourDescriptor() {
  override val markerProcessorFactory: MarkerProcessorFactory
    get() = MarkdownDefaultMarkerProcessor.Factory

  override val sequentialParserManager: SequentialParserManager = DefaultSequentialParserManager()

  override fun createInlinesLexer(): MarkdownLexer {
    return delegate.createInlinesLexer()
  }

  override fun createHtmlGeneratingProviders(linkMap: LinkMap, baseURI: URI?): Map<IElementType, GeneratingProvider> {
    val base = delegate.createHtmlGeneratingProviders(linkMap, baseURI)
    val result = HashMap(base)
    addCustomProviders(result)
    return result
  }

  private fun addCustomProviders(providers: MutableMap<IElementType, GeneratingProvider>) {
    providers[DefinitionListMarkerProvider.DEFINITION_LIST] = SimpleInlineTagProvider("dl")
    providers[DefinitionListMarkerProvider.DEFINITION] = SimpleInlineTagProvider("dd")
    providers[DefinitionListMarkerProvider.TERM] = SimpleInlineTagProvider("dt")
    providers[DefinitionListMarkerProvider.DEFINITION_MARKER] = TransparentInlineHolderProvider()
    providers[FrontMatterHeaderMarkerProvider.FRONT_MATTER_HEADER] = ExcludedElementProvider()
    providers[MarkdownElementTypes.ATX_1] = HeaderGeneratingProvider("h1")
    providers[MarkdownElementTypes.ATX_2] = HeaderGeneratingProvider("h2")
    providers[MarkdownElementTypes.ATX_3] = HeaderGeneratingProvider("h3")
    providers[MarkdownElementTypes.ATX_4] = HeaderGeneratingProvider("h4")
    providers[MarkdownElementTypes.ATX_5] = HeaderGeneratingProvider("h5")
    providers[MarkdownElementTypes.ATX_6] = HeaderGeneratingProvider("h6")
    providers[MarkdownElementTypes.SETEXT_1] = HeaderGeneratingProvider("h1")
    providers[MarkdownElementTypes.SETEXT_2] = HeaderGeneratingProvider("h2")
  }

  class DefaultSequentialParserManager: SequentialParserManager() {
    override fun getParserSequence(): List<SequentialParser> {
      return listOf(
        AutolinkParser(listOf(MarkdownTokenTypes.AUTOLINK, GFMTokenTypes.GFM_AUTOLINK)),
        BacktickParser(),
        ImageParser(),
        InlineLinkParser(),
        ReferenceLinkParser(),
        EmphasisLikeParser(EmphStrongDelimiterParser(), StrikeThroughDelimiterParser())
      )
    }
  }

  private class ExcludedElementProvider: GeneratingProvider {
    override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) = Unit
  }
}
