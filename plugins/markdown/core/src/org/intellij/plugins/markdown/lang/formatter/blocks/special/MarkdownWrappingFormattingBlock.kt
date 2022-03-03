// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.formatter.blocks.special

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.codeStyle.CodeStyleSettings
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.formatter.blocks.MarkdownBlocks
import org.intellij.plugins.markdown.lang.formatter.blocks.MarkdownFormattingBlock
import org.intellij.plugins.markdown.lang.psi.MarkdownAstUtils.children
import org.intellij.plugins.markdown.util.MarkdownTextUtil

/**
 * Markdown special formatting block that puts all it [MarkdownTokenTypes.TEXT] children inside wrap.
 *
 * Allows wrapping paragraphs around right margin. So, it kind of emulates reflow formatting for paragraphs.
 */
internal class MarkdownWrappingFormattingBlock(
  settings: CodeStyleSettings,
  spacing: SpacingBuilder,
  node: ASTNode,
  alignment: Alignment? = null,
  wrap: Wrap? = null
) : MarkdownFormattingBlock(node, settings, spacing, alignment, wrap) {
  /** Number of newlines in this block's text */
  val newlines: Int
    get() = node.text.count { it == '\n' }

  override fun buildChildren(): List<Block> {
    val customSettings = obtainCustomSettings()
    val wrapType = when {
      customSettings.WRAP_TEXT_IF_LONG -> WrapType.NORMAL
      else -> WrapType.NONE
    }
    val wrapping = Wrap.createWrap(wrapType, false)
    val filtered = MarkdownBlocks.filterFromWhitespaces(node.children())
    val result = ArrayList<Block>()
    for (node in filtered) {
      when (node.elementType) {
        MarkdownTokenTypes.TEXT -> {
          val splits = MarkdownTextUtil.getSplitBySpacesRanges(node.text, node.textRange.startOffset)
          for (split in splits) {
            result.add(MarkdownRangedFormattingBlock(node, split, settings, spacing, alignment, wrapping))
          }
        }
        else -> result.add(MarkdownBlocks.create(node, settings, spacing) { alignment })
      }
    }
    return result
  }
}
