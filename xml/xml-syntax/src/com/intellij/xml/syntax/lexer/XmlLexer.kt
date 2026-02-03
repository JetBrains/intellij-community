// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.syntax.lexer

import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.syntaxElementTypeSetOf
import com.intellij.platform.syntax.util.lexer.FlexAdapter
import com.intellij.platform.syntax.util.lexer.MergingLexerAdapter
import com.intellij.xml.syntax.XmlSyntaxTokenType
import org.jetbrains.annotations.ApiStatus
import kotlin.jvm.JvmOverloads

class XmlLexer(baseLexer: Lexer) : MergingLexerAdapter(baseLexer, TOKENS_TO_MERGE) {

  @JvmOverloads
  constructor(conditionalCommentsSupport: Boolean = false) :
    this(_XmlLexer(__XmlLexer(), conditionalCommentsSupport))

}

@ApiStatus.Internal
val TOKENS_TO_MERGE: SyntaxElementTypeSet = syntaxElementTypeSetOf(
  XmlSyntaxTokenType.XML_DATA_CHARACTERS,
  XmlSyntaxTokenType.XML_TAG_CHARACTERS,
  XmlSyntaxTokenType.XML_ATTRIBUTE_VALUE_TOKEN,
  XmlSyntaxTokenType.XML_PI_TARGET,
  XmlSyntaxTokenType.XML_COMMENT_CHARACTERS,
)

class _XmlLexer @JvmOverloads constructor(
  flexLexer: __XmlLexer,
  conditionalCommentsSupport: Boolean = false,
) : FlexAdapter(flexLexer) {

  private var myState: Int = __XmlLexer.YYINITIAL

  init {
    flexLexer.setConditionalCommentsSupport(conditionalCommentsSupport)
  }

  override fun getState(): Int = myState

  private fun packState() {
    val flex = flex as __XmlLexer
    this.myState = ((flex.yyprevstate() and STATE_MASK) shl STATE_SHIFT) or (flex.yystate() and STATE_MASK)
  }

  private fun handleState(initialState: Int) {
    val flex = flex as __XmlLexer
    flex.yybegin(initialState and STATE_MASK)
    flex.pushState((initialState shr STATE_SHIFT) and STATE_MASK)
    packState()
  }

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    super.start(buffer, startOffset, endOffset, initialState)
    handleState(initialState)
  }

  override fun advance() {
    super.advance()
    packState()
  }

  companion object {
    private const val STATE_SHIFT = 5
    private val STATE_MASK = (1 shl STATE_SHIFT) - 1

    init {
      require((STATE_MASK shl 1) <= HtmlLexerConstants.BASE_STATE_MASK)
    }
  }
}