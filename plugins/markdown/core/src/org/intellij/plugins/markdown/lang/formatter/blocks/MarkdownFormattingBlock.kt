// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.formatter.blocks

import com.intellij.formatting.Alignment
import com.intellij.formatting.Block
import com.intellij.formatting.ChildAttributes
import com.intellij.formatting.Indent
import com.intellij.formatting.Spacing
import com.intellij.formatting.SpacingBuilder
import com.intellij.formatting.Wrap
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.common.AbstractBlock
import com.intellij.psi.formatter.common.SettingsAwareBlock
import com.intellij.psi.tree.TokenSet
import com.intellij.util.text.CharArrayUtil
import org.intellij.plugins.markdown.injection.MarkdownCodeFenceUtils
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings
import org.intellij.plugins.markdown.lang.psi.util.children
import org.intellij.plugins.markdown.lang.psi.util.hasType
import org.intellij.plugins.markdown.lang.psi.util.parents
import org.intellij.plugins.markdown.util.MarkdownPsiStructureUtil.isTopLevel

private fun ASTNode.isBlockQuoteContinuationWhitespace(): Boolean {
  return elementType in MarkdownTokenTypeSets.WHITE_SPACES && text.contains('>')
}

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
    private val TEXT_SEPARATED_INLINE_ELEMENTS = TokenSet.create(
      MarkdownElementTypes.EMPH,
      MarkdownElementTypes.STRONG,
      MarkdownElementTypes.STRIKETHROUGH,
      MarkdownTokenTypes.LPAREN,
      MarkdownTokenTypes.RPAREN
    )
  }

  override fun getSettings(): CodeStyleSettings = settings

  protected fun obtainCustomSettings(): MarkdownCustomCodeStyleSettings {
    return settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java)
  }

  override fun isLeaf(): Boolean = subBlocks.isEmpty()

  /**
   * The Markdown lexer folds a nested list item's leading indentation into its `LIST_BULLET`/`LIST_NUMBER` token
   * (e.g. `"  - "`), so a list / list-item block would otherwise start inside the line's indentation. Trim that
   * leading whitespace here so the block starts at its real content; otherwise offset-based consumers such as
   * indent auto-detection (`FormatterBasedLineIndentInfoBuilder`) undercount the indent of nested list lines.
   */
  private val actualTextRange by lazy(LazyThreadSafetyMode.PUBLICATION) {
    val range = node.textRange
    if (node.elementType == MarkdownElementTypes.LIST_ITEM || node.elementType in MarkdownTokenTypeSets.LISTS) {
      val leading = CharArrayUtil.shiftForward(node.chars, 0, " \t")
      if (1 <= leading && leading < range.length) {
        return@lazy TextRange(range.startOffset + leading, range.endOffset)
      }
    }
    range
  }

  override fun getTextRange(): TextRange = actualTextRange

  override fun getSpacing(child1: Block?, child2: Block): Spacing? {
    val continuation = (child1 as? AbstractBlock)?.node?.takeIf { it.isBlockQuoteContinuationWhitespace() }
    if (node.elementType == MarkdownElementTypes.LIST_ITEM && continuation != null
        && (child2 as? AbstractBlock)?.node?.elementType in MarkdownTokenTypeSets.LISTS) {
      val spaces = continuation.text.substringAfter('>').length.coerceAtLeast(1)
      return Spacing.createSpacing(spaces, spaces, 0, false, 0)
    }
    val result = spacing.getSpacing(this, child1, child2)
    if (result != null && isTextGluedToFollowingInline(child1, child2)) {
      return Spacing.createSpacing(0, 0, 0, false, 0)
    }
    return result
  }

  private fun isTextGluedToFollowingInline(child1: Block?, child2: Block): Boolean {
    val node1 = (child1 as? AbstractBlock)?.node ?: return false
    val node2 = (child2 as? AbstractBlock)?.node ?: return false
    return node1.elementType == MarkdownTokenTypes.TEXT
           && node2.elementType in TEXT_SEPARATED_INLINE_ELEMENTS
           && child1.textRange.endOffset == child2.textRange.startOffset
  }

  override fun getIndent(): Indent? {
    if (node.elementType in MarkdownTokenTypeSets.LISTS && node.parents(withSelf = false).any { it.elementType == MarkdownElementTypes.LIST_ITEM }) {
      val listItemParent = node.parents(withSelf = false).firstOrNull { it.elementType == MarkdownElementTypes.LIST_ITEM }
      if (listItemParent != null && listItemParent.children().any { it.elementType == MarkdownElementTypes.TABLE }) {
        return Indent.getNoneIndent()
      }
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
    if (MarkdownCodeFenceUtils.isCodeFence(node) && !node.isTopLevel()) return EMPTY

    return super.getSubBlocks()
  }

  override fun buildChildren(): List<Block> {
    if (!node.canBeFormatted()) {
      return emptyList()
    }
    val newAlignment = Alignment.createAlignment()
    return when (node.elementType) {
      //Code fence alignment is not supported for now because of manipulator problems
      // and the fact that when end of code fence is in blockquote -- parser
      // would treat blockquote as a part of code fence end token
      MarkdownElementTypes.CODE_FENCE, MarkdownElementTypes.CODE_SPAN -> emptyList()
      MarkdownElementTypes.FRONT_MATTER_HEADER -> emptyList()
      MarkdownElementTypes.ADMONITION -> emptyList()
      MarkdownElementTypes.LIST_ITEM -> {
        val hasTableChild = node.children().any { it.elementType == MarkdownElementTypes.TABLE }
        val nonAlignable = if (hasTableChild) MarkdownTokenTypeSets.LIST_MARKERS else NON_ALIGNABLE_LIST_ELEMENTS
        MarkdownBlocks.create(node.children(), settings, spacing) {
          if (it.elementType in nonAlignable || it.isBlockQuoteContinuationWhitespace()) alignment else newAlignment
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

  private fun ASTNode.canBeFormatted(): Boolean {
    return parents(withSelf = true).none { it.hasType(MarkdownElementTypes.TABLE_CELL) }
  }
}
