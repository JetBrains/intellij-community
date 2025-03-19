package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.lang.ASTNode
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class MarkdownComment(node: ASTNode): MarkdownCompositePsiElementBase(node) {
  override fun getPresentableTagName(): String {
    return "comment"
  }
}
