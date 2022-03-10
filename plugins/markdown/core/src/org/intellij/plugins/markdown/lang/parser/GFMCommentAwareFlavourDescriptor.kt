// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.parser

import org.intellij.markdown.IElementType
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.SimpleInlineTagProvider
import org.intellij.markdown.html.TransparentInlineHolderProvider
import org.intellij.markdown.lexer.MarkdownLexer
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkerProcessorFactory
import org.intellij.markdown.parser.sequentialparsers.SequentialParserManager
import org.jetbrains.annotations.ApiStatus
import java.net.URI

@ApiStatus.Internal
class GFMCommentAwareFlavourDescriptor(private val delegate: GFMFlavourDescriptor = GFMFlavourDescriptor()): CommonMarkFlavourDescriptor() {
  override val markerProcessorFactory: MarkerProcessorFactory
    get() = GFMCommentAwareMarkerProcessor.Factory

  override val sequentialParserManager: SequentialParserManager
    get() = delegate.sequentialParserManager

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
  }

  override fun createInlinesLexer(): MarkdownLexer {
    return delegate.createInlinesLexer()
  }
}
