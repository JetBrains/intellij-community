package org.intellij.plugins.markdown.ui.preview.html

import org.intellij.markdown.ast.ASTNode

internal val ASTNode.firstChild: ASTNode?
  get() = children.firstOrNull()

internal val ASTNode.lastChild: ASTNode?
  get() = children.lastOrNull()

internal fun ASTNode.children(): Sequence<ASTNode> {
  return children.asSequence()
}
