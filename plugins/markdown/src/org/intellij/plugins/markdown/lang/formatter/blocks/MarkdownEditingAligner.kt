// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.formatter.blocks

import com.intellij.formatting.Block
import com.intellij.formatting.ChildAttributes
import com.intellij.formatting.Indent
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.formatter.blocks.special.MarkdownWrappingFormattingBlock

object MarkdownEditingAligner {
  private val DEFAULT_ATTRIBUTES = ChildAttributes(Indent.getNoneIndent(), null)

  fun calculateChildAttributes(previous: Block?): ChildAttributes {
    if (previous == null || previous !is MarkdownFormattingBlock) {
      return DEFAULT_ATTRIBUTES
    }

    //chain of last children of previous
    val chain = previous.traverseLastBlocks().toList().reversed()

    return findAttributes(previous, chain) ?: DEFAULT_ATTRIBUTES
  }

  private fun Block.traverseLastBlocks(): Sequence<Block> = sequence<Block> {
    var cur: Block? = this@traverseLastBlocks
    while (cur != null) {
      yield(cur)
      cur = cur.subBlocks.getOrNull(cur.subBlocks.size - 1)
    }
  }

  private fun findAttributes(previous: MarkdownFormattingBlock?, chain: List<Block>): ChildAttributes? {

    var default: ChildAttributes? = null
    for (child in chain) {
      if (child !is MarkdownFormattingBlock) continue

      if (child.node.elementType == MarkdownTokenTypeSets.PARAGRAPH && child is MarkdownWrappingFormattingBlock && child.newlines >= 1) {
        return ChildAttributes(child.indent, child.alignment)
      }
      if (child.node.elementType == MarkdownTokenTypeSets.BLOCK_QUOTE) {
        return ChildAttributes(child.indent, child.alignment)
      }
      if (child.node.elementType in MarkdownTokenTypeSets.LISTS) {
        return ChildAttributes(child.indent, child.alignment)
      }

      if ((child.alignment != null || child.indent != null) && default == null) {
        default = ChildAttributes(child.indent ?: Indent.getNoneIndent(), child.alignment)
      }
    }

    return default
  }
}