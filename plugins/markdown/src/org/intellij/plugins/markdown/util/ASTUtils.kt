// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.util

import com.intellij.lang.ASTNode

private fun ASTNode.traverse(next: (ASTNode) -> ASTNode?): Sequence<ASTNode> = sequence<ASTNode> {
  var cur = next(this@traverse)
  while (cur != null) {
    yield(cur)
    cur = next(cur)
  }
}

/**
 * Get all direct children of [this] node.
 * Does not include [this]
 */
internal fun ASTNode.children(): Sequence<ASTNode> = sequence {
  val first = this@children.firstChildNode ?: return@sequence
  yield(first)
  yieldAll(first.nextSiblings())
}

/**
 * Get parent chain of this node.
 * Does not include [this]
 */
internal fun ASTNode.parents(): Sequence<ASTNode> = traverse { it.treeParent }

/**
 * Get previous siblings of [this] node.
 * Does not include [this]
 */
internal fun ASTNode.prevSiblings(): Sequence<ASTNode> = traverse { it.treePrev }
/**
 * Get next siblings of [this] node.
 * Does not include [this]
 */
internal fun ASTNode.nextSiblings(): Sequence<ASTNode> = traverse { it.treeNext }
