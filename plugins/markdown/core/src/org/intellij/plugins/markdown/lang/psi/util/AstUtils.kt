// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.psi.util

import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiUtilCore

internal fun ASTNode.hasType(type: IElementType): Boolean {
  return PsiUtilCore.getElementType(this) == type
}

internal fun ASTNode.hasType(type: TokenSet): Boolean {
  return PsiUtilCore.getElementType(this) in type
}

internal fun ASTNode.traverse(withSelf: Boolean, next: (ASTNode) -> ASTNode?): Sequence<ASTNode> {
  return sequence {
    if (withSelf) {
      yield(this@traverse)
    }
    var current = next(this@traverse)
    while (current != null) {
      yield(current)
      current = next(current)
    }
  }
}

internal fun ASTNode.children(): Sequence<ASTNode> {
  return sequence {
    val first = this@children.firstChildNode ?: return@sequence
    yieldAll(first.siblings(forward = true, withSelf = true))
  }
}

internal fun ASTNode.parents(withSelf: Boolean): Sequence<ASTNode> {
  return traverse(withSelf) { it.treeParent }
}

internal fun ASTNode.siblings(forward: Boolean, withSelf: Boolean): Sequence<ASTNode> {
  return when {
    forward -> traverse(withSelf) { it.treeNext }
    else -> traverse(withSelf) { it.treePrev }
  }
}
