// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.formatter.blocks.special

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.codeStyle.CodeStyleSettings
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.formatter.blocks.MarkdownBlocks
import org.intellij.plugins.markdown.lang.formatter.blocks.MarkdownFormattingBlock
import org.intellij.plugins.markdown.util.MarkdownTextUtil
import org.intellij.plugins.markdown.util.children


/**
 * Markdown special formatting block that makes all
 * [MarkdownTokenTypes.TEXT] elements inside wrap.
 *
 * It is used to make markdown paragraph wrap around
 * right margin. So, it kind of emulates reflow formatting
 * for paragraphs.
 */
internal class MarkdownWrappingFormattingBlock(
  settings: CodeStyleSettings, spacing: SpacingBuilder,
  node: ASTNode, alignment: Alignment? = null, wrap: Wrap? = null
) : MarkdownFormattingBlock(node, settings, spacing, alignment, wrap) {

  companion object {
    private val NORMAL_WRAP = Wrap.createWrap(WrapType.NORMAL, false)
  }

  override fun buildChildren(): List<Block> {
    val filtered = MarkdownBlocks.filterFromWhitespaces(node.children())

    val result = ArrayList<Block>()

    for (node in filtered) {
      if (node.elementType == MarkdownTokenTypes.TEXT) {
        val splits = MarkdownTextUtil.getSplitBySpacesRanges(node.text, node.textRange.startOffset)

        for (split in splits) {
          result.add(MarkdownRangedFormattingBlock(node, split, settings, spacing, alignment, NORMAL_WRAP))
        }
      }
      else {
        result.add(MarkdownBlocks.create(node, settings, spacing) { alignment })
      }
    }

    return result
  }
}
