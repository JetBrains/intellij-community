// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.parser

import org.intellij.markdown.IElementType
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.lexer.MarkdownLexer
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkerProcessorFactory
import org.intellij.markdown.parser.sequentialparsers.SequentialParserManager
import java.net.URI

internal class GFMCommentAwareFlavourDescriptor(private val myGfmFlavourDescriptor: GFMFlavourDescriptor = GFMFlavourDescriptor())
  : CommonMarkFlavourDescriptor() {

  override val markerProcessorFactory: MarkerProcessorFactory get() = GFMCommentAwareMarkerProcessor.Factory

  override val sequentialParserManager: SequentialParserManager get() = myGfmFlavourDescriptor.sequentialParserManager

  override fun createHtmlGeneratingProviders(linkMap: LinkMap, baseURI: URI?): Map<IElementType, GeneratingProvider> {
    return myGfmFlavourDescriptor.createHtmlGeneratingProviders(linkMap, baseURI)
  }

  override fun createInlinesLexer(): MarkdownLexer {
    return myGfmFlavourDescriptor.createInlinesLexer()
  }
}
