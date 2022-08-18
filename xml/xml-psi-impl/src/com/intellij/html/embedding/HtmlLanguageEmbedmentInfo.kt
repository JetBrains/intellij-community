// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.embedding

import com.intellij.lang.Language
import com.intellij.lexer.Lexer
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.psi.tree.IElementType

class HtmlLanguageEmbedmentInfo(private val elementType: IElementType, private val syntaxHighlighterLanguage: Language): HtmlEmbedmentInfo {

  override fun getElementType(): IElementType = elementType

  override fun createHighlightingLexer(): Lexer =
    SyntaxHighlighterFactory.getSyntaxHighlighter(syntaxHighlighterLanguage, null, null).highlightingLexer

}