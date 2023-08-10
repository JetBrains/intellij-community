// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.html.embedding

import com.intellij.lexer.BaseHtmlLexer
import com.intellij.lexer.Lexer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import java.util.function.Function
import java.util.function.Supplier

open class HtmlTokenEmbeddedContentProvider : BaseHtmlEmbeddedContentProvider {

  private var tokenType: IElementType? = null
  private val tokens: TokenSet

  private val info: HtmlEmbedmentInfo

  @JvmOverloads
  constructor(lexer: BaseHtmlLexer,
              token: IElementType,
              highlightingLexerSupplier: Supplier<Lexer?>,
              elementTypeOverrideSupplier: Supplier<IElementType?> = Supplier { token }
  ) : super(lexer) {
    tokens = TokenSet.create(token)
    info = object : HtmlEmbedmentInfo {
      override fun getElementType(): IElementType? = elementTypeOverrideSupplier.get()
      override fun createHighlightingLexer(): Lexer? = highlightingLexerSupplier.get()
    }
  }

  @JvmOverloads
  constructor(
    lexer: BaseHtmlLexer,
    tokens: TokenSet,
    highlightingLexerSupplier: Function<IElementType, Lexer?>,
    elementTypeOverrideSupplier: Function<IElementType, IElementType?> = Function { it },
  ) : super(lexer) {
    this.tokens = tokens
    info = object : HtmlEmbedmentInfo {
      override fun getElementType(): IElementType? = elementTypeOverrideSupplier.apply(tokenType!!)
      override fun createHighlightingLexer(): Lexer? = highlightingLexerSupplier.apply(tokenType!!)
    }
  }

  override fun isStartOfEmbedment(tokenType: IElementType): Boolean = tokens.contains(tokenType)

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
    embedment = tokens.contains(tokenType)
    if (embedment) {
      this.tokenType = tokenType
    }
    else {
      this.tokenType = null
    }
  }

}