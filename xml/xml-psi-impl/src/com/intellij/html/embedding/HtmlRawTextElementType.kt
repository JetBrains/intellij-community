// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.html.embedding

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.html.HTMLLanguage
import com.intellij.lexer.HtmlRawTextLexer
import com.intellij.lexer.Lexer
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.html.HtmlRawTextImpl
import com.intellij.psi.tree.IStrongWhitespaceHolderElementType
import com.intellij.psi.xml.XmlElementType.XML_TEXT

object HtmlRawTextElementType : HtmlCustomEmbeddedContentTokenType("HTML_RAW_TEXT", HTMLLanguage.INSTANCE),
                                IStrongWhitespaceHolderElementType {

  override fun parse(builder: PsiBuilder) {
    val start = builder.mark()
    while (!builder.eof()) {
      builder.advanceLexer()
    }
    start.done(XML_TEXT)
  }

  override fun createLexer(): Lexer = HtmlRawTextLexer()

  override fun createPsi(node: ASTNode): PsiElement = HtmlRawTextImpl(node)
}