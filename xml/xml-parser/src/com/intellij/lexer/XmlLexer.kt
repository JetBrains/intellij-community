// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lexer

import com.intellij.psi.tree.TokenSet
import com.intellij.psi.xml.XmlTokenType

class XmlLexer(baseLexer: Lexer) :
  MergingLexerAdapter(baseLexer, TOKENS_TO_MERGE) {

  @JvmOverloads
  constructor(conditionalCommentsSupport: Boolean = false) :
    this(_XmlLexer(__XmlLexer(null), conditionalCommentsSupport))

  private companion object {
    private val TOKENS_TO_MERGE = TokenSet.create(
      XmlTokenType.XML_DATA_CHARACTERS,
      XmlTokenType.XML_TAG_CHARACTERS,
      XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN,
      XmlTokenType.XML_PI_TARGET,
      XmlTokenType.XML_COMMENT_CHARACTERS,
    )
  }
}
