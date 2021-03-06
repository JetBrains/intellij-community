// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.html.embedding

import com.intellij.lexer.Lexer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.tree.IElementType

class HtmlEmbedment(private val htmlEmbedmentInfo: HtmlEmbedmentInfo,
                    val range: TextRange,
                    val baseLexerState: Int) {

  fun getElementType(): IElementType? = htmlEmbedmentInfo.getElementType()
  fun createHighlightingLexer(): Lexer? = htmlEmbedmentInfo.createHighlightingLexer()
}