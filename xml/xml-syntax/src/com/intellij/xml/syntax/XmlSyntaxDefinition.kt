// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.syntax

import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.element.SyntaxTokenTypes
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.syntaxElementTypeSetOf
import com.intellij.xml.syntax.lexer.XmlLexer
import kotlin.jvm.JvmStatic

object XmlSyntaxDefinition {
  val WHITESPACES: SyntaxElementTypeSet = syntaxElementTypeSetOf(SyntaxTokenTypes.WHITE_SPACE)

  val COMMENTS: SyntaxElementTypeSet = syntaxElementTypeSetOf(
    XmlSyntaxTokenType.XML_COMMENT_START,
    XmlSyntaxTokenType.XML_COMMENT_CHARACTERS,
    XmlSyntaxTokenType.XML_COMMENT_END,
  )

  @JvmStatic
  fun createLexer(): Lexer = XmlLexer()
}