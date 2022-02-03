// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.formatter.blocks

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.common.AbstractBlock
import com.intellij.psi.formatter.common.SettingsAwareBlock
import com.intellij.psi.tree.TokenSet
import org.intellij.plugins.markdown.injection.MarkdownCodeFenceUtils
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings
import org.intellij.plugins.markdown.lang.psi.MarkdownAstUtils.children
import org.intellij.plugins.markdown.lang.psi.MarkdownAstUtils.parents
import org.intellij.plugins.markdown.util.MarkdownPsiUtil

/**
 * Formatting block used by markdown plugin
 *
 * It defines alignment equal for all block on the same line, and new for inner lists
 */
internal open class MarkdownFormattingBlock(
  node: ASTNode,
  private val settings: CodeStyleSettings,
  protected val spacing: SpacingBuilder,
  alignment: Alignment? = null,
  wrap: Wrap? = null
) : AbstractBlock(node, wrap, alignment), SettingsAwareBlock {

  companion object {
    private val NON_ALIGNABLE_LIST_ELEMENTS = TokenSet.orSet(MarkdownTokenTypeSets.LIST_MARKERS, MarkdownTokenTypeSets.LISTS)
  }

  override fun getSettings(): CodeStyleSettings = settings

  protected fun obtainCustomSettings(): MarkdownCustomCodeStyleSettings {
    return settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java)
  }

  override fun isLeaf(): Boolean = subBlocks.isEmpty()

  override fun getSpacing(child1: Block?, child2: Block): Spacing? = spacing.getSpacing(this, child1, child2)

  override fun getIndent(): Indent? {
    if (node.elementType in MarkdownTokenTypeSets.LISTS && node.parents(withSelf = false).any { it.elementType == MarkdownElementTypes.LIST_ITEM }) {
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
    return MarkdownEditingAligner.calculateChildAttributes(subBlocks.getOrNull(newChildIndex - 1))
  }

  override fun getSubBlocks(): List<Block?> {
    //Non top-level codefences cannot be formatted correctly even with correct inject, so -- just ignore it
    if (MarkdownCodeFenceUtils.isCodeFence(node) && !MarkdownPsiUtil.isTopLevel(node)) return EMPTY

    return super.getSubBlocks()
  }

  override fun buildChildren(): List<Block> {
    val newAlignment = Alignment.createAlignment()

    return when (node.elementType) {
      //Code fence alignment is not supported for now because of manipulator problems
      // and the fact that when end of code fence is in blockquote -- parser
      // would treat blockquote as a part of code fence end token
      MarkdownElementTypes.CODE_FENCE -> emptyList()
      MarkdownElementTypes.LIST_ITEM -> {
        MarkdownBlocks.create(node.children(), settings, spacing) {
          if (it.elementType in NON_ALIGNABLE_LIST_ELEMENTS) alignment else newAlignment
        }.toList()
      }
      MarkdownElementTypes.PARAGRAPH, MarkdownElementTypes.CODE_BLOCK, MarkdownElementTypes.BLOCK_QUOTE -> {
        MarkdownBlocks.create(node.children(), settings, spacing) { alignment ?: newAlignment }.toList()
      }
      else -> {
        MarkdownBlocks.create(node.children(), settings, spacing) { alignment }.toList()
      }
    }
  }
}
