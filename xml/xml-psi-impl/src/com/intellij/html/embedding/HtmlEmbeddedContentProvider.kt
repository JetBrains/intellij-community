// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.html.embedding

import com.intellij.lexer.HtmlRawTextLexer
import com.intellij.lexer.Lexer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.tree.IElementType
import com.intellij.psi.xml.XmlTokenType

interface HtmlEmbeddedContentProvider {
  fun handleToken(tokenType: IElementType, range: TextRange)
  fun createEmbedment(tokenType: IElementType): HtmlEmbedment?
  fun clearEmbedment()
  fun hasState(): Boolean
  fun getState(): Any?
  fun restoreState(state: Any?)

  companion object {
    @JvmField
    val RAW_TEXT_EMBEDMENT: HtmlEmbedmentInfo = object : HtmlEmbedmentInfo {
      override fun getElementType(): IElementType? = XmlTokenType.XML_DATA_CHARACTERS
      override fun createHighlightingLexer(): Lexer? = HtmlRawTextLexer()
    }

    @JvmField
    val RAW_TEXT_FORMATTABLE_EMBEDMENT: HtmlEmbedmentInfo = object : HtmlEmbedmentInfo {
      override fun getElementType(): IElementType? = HtmlRawTextElementType
      override fun createHighlightingLexer(): Lexer? = HtmlRawTextLexer()
    }
  }
}