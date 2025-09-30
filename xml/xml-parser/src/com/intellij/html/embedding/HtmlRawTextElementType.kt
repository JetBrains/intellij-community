// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.html.embedding

import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.lang.PsiBuilder
import com.intellij.lang.html.HTMLLanguage
import com.intellij.lexer.HtmlRawTextLexer
import com.intellij.lexer.Lexer
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IStrongWhitespaceHolderElementType
import com.intellij.psi.xml.XmlElementType.XML_TEXT

open class HtmlRawTextElementType : HtmlCustomEmbeddedContentTokenType, IStrongWhitespaceHolderElementType {

  constructor(): this("HTML_RAW_TEXT", HTMLLanguage.INSTANCE)

  constructor(debugName: String, language: Language) : super(debugName, language)

  override fun parse(builder: PsiBuilder) {
    val start = builder.mark()
    while (!builder.eof()) {
      builder.advanceLexer()
    }
    start.done(XML_TEXT)
  }

  override fun createLexer(): Lexer =
    HtmlRawTextLexer()

  override fun createPsi(node: ASTNode): PsiElement =
    service<BasicHtmlRawTextElementFactory>()
      .createRawTextElement(node)
}