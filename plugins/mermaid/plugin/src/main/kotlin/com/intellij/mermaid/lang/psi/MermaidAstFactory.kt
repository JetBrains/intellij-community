package com.intellij.mermaid.lang.psi

import com.intellij.lang.ASTFactory
import com.intellij.mermaid.lang.parser.ParserUtils
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.tree.IElementType

class MermaidAstFactory: ASTFactory() {
  override fun createLeaf(type: IElementType, text: CharSequence): LeafElement? {
    return if (type === ParserUtils.DIRECTIVE_VALUE) {
      MermaidDirectiveValue(type, text)
    } else super.createLeaf(type, text)
  }
}
