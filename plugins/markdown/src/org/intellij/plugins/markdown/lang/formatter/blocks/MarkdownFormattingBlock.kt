// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.formatter.blocks

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.common.AbstractBlock
import com.intellij.psi.formatter.common.SettingsAwareBlock
import com.intellij.psi.tree.TokenSet
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.util.children
import org.intellij.plugins.markdown.util.parents

/**
 * Formatting block used by markdown plugin
 *
 * It defines **2** spaces indent for inner lists (2 more spaces added via MarkdownFormattingPostProcessor)
 * It defines alignment equal for all block on the same line, and new for inner lists
 */
internal open class MarkdownFormattingBlock(
  private val settings: CodeStyleSettings, private val spacing: SpacingBuilder,
  node: ASTNode, alignment: Alignment? = null
) : AbstractBlock(node, null, alignment), SettingsAwareBlock {
  companion object {
    private val DEFAULT_ATTRIBUTES = ChildAttributes(Indent.getNoneIndent(), null)

    private val NON_ALIGNABLE_LIST_ELEMENTS = TokenSet.orSet(MarkdownTokenTypeSets.LIST_MARKERS, MarkdownTokenTypeSets.LISTS)
  }

  override fun getSettings(): CodeStyleSettings = settings

  override fun isLeaf(): Boolean = subBlocks.size == 0

  override fun getSpacing(child1: Block?, child2: Block): Spacing? {
    return spacing.getSpacing(this, child1, child2)
  }

  override fun getIndent(): Indent? {
    if (node.elementType in MarkdownTokenTypeSets.LISTS && node.parents().any { it.elementType == MarkdownElementTypes.LIST_ITEM }) {
      return Indent.getNormalIndent()
    }
    return Indent.getNoneIndent()
  }

  /**
   *  Get indent for new formatting block, that will be created when user will start typing after enter
   *  In general `getChildAttributes` defines where to move caret after enter.
   *  Other changes (for example, adding of `>` for blockquote) are handled by MarkdownEnterHandler
   */
  override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
    if (newChildIndex <= 0) return DEFAULT_ATTRIBUTES

    return ChildAttributes.DELEGATE_TO_PREV_CHILD
  }

  override fun buildChildren(): List<Block> {
    return when (node.elementType) {
      //Code fence alignment is not supported for now because of manipulator problems
      // and the fact that when end of code fence is in blockquote -- parser
      // would treat blockquote as a part of code fence end token
      MarkdownElementTypes.CODE_FENCE -> emptyList()
      MarkdownElementTypes.LIST_ITEM -> {
        val list = Alignment.createAlignment()
        MarkdownBlocks.filterFromWhitespaces(node.children()).map {
          val alignment = if (it.elementType in NON_ALIGNABLE_LIST_ELEMENTS) this.alignment else list
          MarkdownBlocks.create(it, settings, alignment, spacing)
        }.toList()
      }
      MarkdownElementTypes.PARAGRAPH, MarkdownElementTypes.CODE_BLOCK, MarkdownElementTypes.BLOCK_QUOTE -> {
        val alignment = alignment ?: Alignment.createAlignment()
        MarkdownBlocks.create(node.children(), settings, alignment, spacing).toList()
      }
      else -> MarkdownBlocks.create(node.children(), settings, alignment, spacing).toList()
    }
  }
}