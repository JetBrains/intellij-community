package org.intellij.plugins.markdown.lang.psi.util

import com.intellij.openapi.util.TextRange
import org.intellij.markdown.IElementType
import org.intellij.markdown.ast.ASTNode

internal fun ASTNode.hasType(type: IElementType): Boolean {
  return this.type == type
}

internal fun ASTNode.hasType(vararg types: IElementType): Boolean {
  return this.type in types
}

internal val ASTNode.textRange: TextRange
  get() = TextRange(startOffset, endOffset)
