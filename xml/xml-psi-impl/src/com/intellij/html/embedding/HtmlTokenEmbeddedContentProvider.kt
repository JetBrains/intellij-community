// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.html.embedding

import com.intellij.lexer.BaseHtmlLexer
import com.intellij.lexer.Lexer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.tree.IElementType
import java.util.function.Supplier

open class HtmlTokenEmbeddedContentProvider
@JvmOverloads constructor(lexer: BaseHtmlLexer,
                          private val token: IElementType,
                          highlightingLexerSupplier: Supplier<Lexer?>,
                          elementTypeOverrideSupplier: Supplier<IElementType?> = Supplier { token })
  : BaseHtmlEmbeddedContentProvider(lexer) {

  private val info = object : HtmlEmbedmentInfo {
    override fun getElementType(): IElementType? = elementTypeOverrideSupplier.get()
    override fun createHighlightingLexer(): Lexer? = highlightingLexerSupplier.get()
  }

  override fun isStartOfEmbedment(tokenType: IElementType): Boolean = tokenType == this.token

  override fun createEmbedmentInfo(): HtmlEmbedmentInfo? = info

  override fun findTheEndOfEmbedment(): Pair<Int, Int> {
    val baseLexer = lexer.delegate
    val position = baseLexer.currentPosition
    baseLexer.advance()
    val result = Pair(baseLexer.tokenStart, baseLexer.state)
    baseLexer.restore(position)
    return result
  }

  override fun handleToken(tokenType: IElementType, range: TextRange) {
    embedment = tokenType == this.token
  }

}