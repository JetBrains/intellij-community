// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lexer

import com.intellij.platform.syntax.psi.asTokenSet
import com.intellij.platform.syntax.psi.lexer.LexerAdapter
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.xml.xmlElementTypeConverter
import com.intellij.xml.syntax.lexer.XmlLexer
import org.jetbrains.annotations.ApiStatus

/**
 * Obsolete: Use [com.intellij.xml.syntax.lexer.XmlLexer] instead. This class is kept for binary compatibility and won't be developed further.
 *
 * If you still need an instance of [com.intellij.lexer.Lexer], use [createXmlLexer] function which returns a new XmlLexer instance wrapped into an adapter.
 */
@ApiStatus.Obsolete
class XmlLexer(baseLexer: Lexer) : MergingLexerAdapter(baseLexer, TOKENS_TO_MERGE) {

  @Suppress("unused") // used by external plugins
  @JvmOverloads
  constructor(conditionalCommentsSupport: Boolean = false) : this(_XmlLexer(__XmlLexer(null), conditionalCommentsSupport))
}

@JvmOverloads
fun createXmlLexer(conditionalCommentsSupport: Boolean = false): Lexer =
  LexerAdapter(
    XmlLexer(conditionalCommentsSupport),
    xmlElementTypeConverter
  )

private val TOKENS_TO_MERGE: TokenSet =
  com.intellij.xml.syntax.lexer.TOKENS_TO_MERGE.asTokenSet(xmlElementTypeConverter)
