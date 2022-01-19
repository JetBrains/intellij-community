// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.psi

import com.intellij.lang.ASTNode

internal object MarkdownAstUtils {
  fun ASTNode.traverse(withSelf: Boolean, next: (ASTNode) -> ASTNode?): Sequence<ASTNode> {
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

  fun ASTNode.children(): Sequence<ASTNode> {
    return sequence {
      val first = this@children.firstChildNode ?: return@sequence
      yieldAll(first.siblings(forward = true, withSelf = true))
    }
  }

  fun ASTNode.parents(withSelf: Boolean): Sequence<ASTNode> {
    return traverse(withSelf) { it.treeParent }
  }

  fun ASTNode.siblings(forward: Boolean, withSelf: Boolean): Sequence<ASTNode> {
    return when {
      forward -> traverse(withSelf) { it.treeNext }
      else -> traverse(withSelf) { it.treePrev }
    }
  }
}
