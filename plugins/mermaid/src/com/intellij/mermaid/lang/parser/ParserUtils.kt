// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.parser

import com.intellij.lang.PsiBuilder
import com.intellij.mermaid.lang.lexer.MermaidTokens.Directives.CLOSE_DIRECTIVE
import com.intellij.mermaid.lang.lexer.MermaidTokens.Frontmatter.FRONTMATTER_END
import com.intellij.mermaid.lang.psi.MermaidElementType
import com.intellij.psi.tree.IElementType

object ParserUtils {

  @JvmField
  val DIRECTIVE_VALUE: IElementType = MermaidElementType("DIRECTIVE_VALUE")

  @JvmField
  val FRONTMATTER_CONTENT: IElementType = MermaidElementType("FRONTMATTER_CONTENT")

  @JvmStatic
  fun directiveParser(builder: PsiBuilder, level: Int): Boolean {
    if (builder.eof()) return false
    val mark = builder.mark()
    while (!builder.eof() && builder.tokenType != CLOSE_DIRECTIVE) {
      builder.advanceLexer()
    }
    mark.collapse(DIRECTIVE_VALUE)
    return true
  }

  @JvmStatic
  fun frontmatterParser(builder: PsiBuilder, level: Int): Boolean {
    if (builder.eof()) return false
    val mark = builder.mark()
    while (!builder.eof() && builder.tokenType != FRONTMATTER_END) {
      builder.advanceLexer()
    }
    mark.collapse(FRONTMATTER_CONTENT)
    return true
  }
}
