// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.formatter.blocks.special

import com.intellij.formatting.Alignment
import com.intellij.formatting.SpacingBuilder
import com.intellij.formatting.Wrap
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CodeStyleSettings
import org.intellij.plugins.markdown.lang.formatter.blocks.MarkdownFormattingBlock
import org.intellij.plugins.markdown.util.MarkdownTextUtil

/**
 * Markdown special formatting block that covers
 * only a part of [myNode] covered by [range]
 *
 * Such blocks can be used to split node into few
 * formatting blocks by whitespaces
 * (e.g. split paragraph into words)
 */
internal class MarkdownRangedFormattingBlock(
  node: ASTNode,
  private val range: TextRange,
  settings: CodeStyleSettings, spacing: SpacingBuilder, alignment: Alignment? = null, wrap: Wrap? = null)
  : MarkdownFormattingBlock(node, settings, spacing, alignment, wrap) {

  override fun getTextRange(): TextRange = range

  companion object {
    fun trimmed(
      node: ASTNode, settings: CodeStyleSettings, spacing: SpacingBuilder, alignment: Alignment?, wrap: Wrap?
    ): MarkdownRangedFormattingBlock {
      val range = MarkdownTextUtil.getTrimmedRange(node.text, node.textRange.startOffset)
      return MarkdownRangedFormattingBlock(node, range, settings, spacing, alignment, wrap)
    }
  }
}