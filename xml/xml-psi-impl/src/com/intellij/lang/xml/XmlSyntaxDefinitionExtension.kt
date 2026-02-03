// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.xml

import com.intellij.platform.syntax.LanguageSyntaxDefinition
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.xml.syntax.XmlParser
import com.intellij.xml.syntax.XmlSyntaxDefinition

class XmlSyntaxDefinitionExtension : LanguageSyntaxDefinition {
  fun getWhitespaceTokens(): SyntaxElementTypeSet = XmlSyntaxDefinition.WHITESPACES

  override fun parse(builder: SyntaxTreeBuilder) {
    XmlParser().parse(builder)
  }

  override fun createLexer(): Lexer = XmlSyntaxDefinition.createLexer()

  override val comments: SyntaxElementTypeSet
    get() = XmlSyntaxDefinition.COMMENTS
}