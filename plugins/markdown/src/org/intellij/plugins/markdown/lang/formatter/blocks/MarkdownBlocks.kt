// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.formatter.blocks

import com.intellij.formatting.Alignment
import com.intellij.formatting.SpacingBuilder
import com.intellij.lang.ASTNode
import com.intellij.psi.codeStyle.CodeStyleSettings
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.formatter.blocks.special.MarkdownRangedFormattingBlock
import org.intellij.plugins.markdown.lang.formatter.blocks.special.MarkdownWrappingFormattingBlock
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings
import org.intellij.plugins.markdown.lang.psi.MarkdownAstUtils.parents
import org.intellij.plugins.markdown.util.hasType

internal object MarkdownBlocks {
  /**
   * Create formatting blocks from sequence of nodes
   *
   * Would ignore real whitespace blocks (blocks which has type whitespace
   * and text of which is really blank)
   */
  fun create(
    nodes: Sequence<ASTNode>,
    settings: CodeStyleSettings,
    spacing: SpacingBuilder,
    align: (ASTNode) -> Alignment?
  ): Sequence<MarkdownFormattingBlock> {
    return filterFromWhitespaces(nodes).map { create(it, settings, spacing, align) }
  }

  /**
   * Create formatting block from node.
   * Would not ignore real whitespace blocks
   */
  fun create(node: ASTNode, settings: CodeStyleSettings, spacing: SpacingBuilder, align: (ASTNode) -> Alignment?): MarkdownFormattingBlock {
    return when (node.elementType) {
      in MarkdownTokenTypeSets.LIST_MARKERS, in MarkdownTokenTypeSets.WHITE_SPACES, MarkdownTokenTypes.BLOCK_QUOTE -> {
        MarkdownRangedFormattingBlock.trimmed(node, settings, spacing, align(node), null)
      }
      in elementsToWrap -> {
        when {
          isInsideBlockquote(node) && !shouldWrapInsideBlockquote(settings) -> MarkdownFormattingBlock(node, settings, spacing, align(node))
          else -> MarkdownWrappingFormattingBlock(settings, spacing, node, align(node))
        }
      }
      else -> MarkdownFormattingBlock(node, settings, spacing, align(node))
    }
  }

  private fun isInsideBlockquote(node: ASTNode): Boolean {
    return node.parents(withSelf = false).any { it.hasType(MarkdownTokenTypeSets.BLOCK_QUOTE) }
  }

  private fun shouldWrapInsideBlockquote(settings: CodeStyleSettings): Boolean {
    val customSettings = settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java)
    return customSettings.WRAP_TEXT_IF_LONG && customSettings.WRAP_TEXT_INSIDE_BLOCKQUOTES
  }

  /** Filter out real whitespace blocks from sequence */
  fun filterFromWhitespaces(sequence: Sequence<ASTNode>) = sequence.filter {
    it.elementType !in MarkdownTokenTypeSets.WHITE_SPACES
    // Dirty hack cause for some reason Markdown parser thinks that `>`, `:` are whitespaces
    || (it.elementType in MarkdownTokenTypeSets.WHITE_SPACES && it.text.isNotBlank())
  }

  private val elementsToWrap = hashSetOf(
    MarkdownElementTypes.PARAGRAPH,
    MarkdownElementTypes.EMPH,
    MarkdownElementTypes.STRONG,
    MarkdownElementTypes.STRIKETHROUGH
  )
}
