package com.intellij.mermaid.lang.psi

import com.intellij.lang.ASTFactory
import com.intellij.mermaid.lang.lexer.MermaidTokens.LINE_COMMENT
import com.intellij.mermaid.lang.parser.ParserUtils.DIRECTIVE_VALUE
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.tree.IElementType

class MermaidAstFactory: ASTFactory() {
  override fun createLeaf(type: IElementType, text: CharSequence): LeafElement? {
    return when(type) {
      DIRECTIVE_VALUE -> MermaidDirectiveValue(type, text)
      LINE_COMMENT -> super.createLeaf(type, text)
      else -> MermaidLeafPsiElement(type, text)
    }
  }
}
