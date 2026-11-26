// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.formatter.blocks

import com.intellij.formatting.Alignment
import com.intellij.formatting.SpacingBuilder
import com.intellij.formatting.Wrap
import com.intellij.formatting.WrapType
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.tree.TokenSet
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.formatter.blocks.MarkdownTextUtil.isFormatterWhitespace
import org.intellij.plugins.markdown.lang.formatter.blocks.special.EmphasisFormattingBlock
import org.intellij.plugins.markdown.lang.formatter.blocks.special.MarkdownRangedFormattingBlock
import org.intellij.plugins.markdown.lang.formatter.blocks.special.MarkdownWrappingFormattingBlock
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings
import org.intellij.plugins.markdown.lang.psi.util.hasType
import org.intellij.plugins.markdown.lang.psi.util.parents

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
    quoteMarkerAlignment: Alignment? = null,
    align: (ASTNode) -> Alignment?
  ): Sequence<MarkdownFormattingBlock> {
    return filterFromWhitespaces(nodes).map { create(it, settings, spacing, null, quoteMarkerAlignment, align) }
  }

  /**
   * Create formatting block from node.
   * Would not ignore real whitespace blocks
   *
   * @param quoteMarkerAlignment the alignment of a `>` marker in [node], see [MarkdownFormattingBlock]
   */
  @JvmOverloads
  fun create(
    node: ASTNode,
    settings: CodeStyleSettings,
    spacing: SpacingBuilder,
    wrap: Wrap? = null,
    quoteMarkerAlignment: Alignment? = null,
    align: (ASTNode) -> Alignment?
  ): MarkdownFormattingBlock {
    return when (node.elementType) {
      in MarkdownTokenTypeSets.LIST_MARKERS, in MarkdownTokenTypeSets.WHITE_SPACES -> {
        MarkdownRangedFormattingBlock.trimmed(node, settings, spacing, align(node), wrap)
      }
      MarkdownTokenTypes.BLOCK_QUOTE -> {
        MarkdownRangedFormattingBlock.trimmed(node, settings, spacing, quoteMarkerAlignment ?: align(node), wrap)
      }
      MarkdownTokenTypes.TABLE_SEPARATOR -> TableSeparatorFormattingBlock(node, settings, spacing, align(node), wrap)
      MarkdownElementTypes.CODE_SPAN -> MarkdownFormattingBlock(
        node, settings, spacing, align(node),
        wrap ?: Wrap.createWrap(if (shouldWrapText(node, settings)) WrapType.NORMAL else WrapType.NONE, false)
      )
      in linkLikeElements -> MarkdownFormattingBlock(
        node, settings, spacing, align(node),
        wrap ?: if (shouldWrapLinkLikeElement(node, settings)) Wrap.createWrap(WrapType.NORMAL, false) else null,
        quoteMarkerAlignment
      )
      in MarkdownTokenTypeSets.EMPHASIS_LIKE_ELEMENTS -> EmphasisFormattingBlock(settings, spacing, node, align(node), wrap, quoteMarkerAlignment)
      MarkdownElementTypes.PARAGRAPH -> when {
        isInsideBlockquote(node) && !shouldWrapText(node, settings) -> MarkdownFormattingBlock(node, settings, spacing, align(node), wrap)
        else -> MarkdownWrappingFormattingBlock(settings, spacing, node, align(node), wrap)
      }
      else -> MarkdownFormattingBlock(node, settings, spacing, align(node), wrap, quoteMarkerAlignment)
    }
  }

  private fun isInsideBlockquote(node: ASTNode): Boolean {
    return node.parents(withSelf = false).any { it.hasType(MarkdownTokenTypeSets.BLOCK_QUOTE) }
  }

  internal fun shouldWrapText(node: ASTNode, settings: CodeStyleSettings): Boolean {
    val customSettings = settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java)
    return customSettings.WRAP_TEXT_IF_LONG
           && node.parents(withSelf = true).none { it.hasType(MarkdownTokenTypeSets.ATX_HEADERS) }
           && (customSettings.WRAP_TEXT_INSIDE_BLOCKQUOTES || !isInsideBlockquote(node))
  }

  /**
   * A link-like element gets a wrap only inside a paragraph, after a whitespace of the same paragraph.
   * A link-like element inside another one gets no wrap.
   * In a paragraph of a list item, it also needs at least two words before it in the same paragraph.
   */
  private fun shouldWrapLinkLikeElement(node: ASTNode, settings: CodeStyleSettings): Boolean {
    if (!shouldWrapText(node, settings)) return false
    if (node.parents(withSelf = false).any { it.elementType in linkLikeElements }) return false
    val paragraph = node.parents(withSelf = false).firstOrNull { it.elementType == MarkdownElementTypes.PARAGRAPH } ?: return false
    if (!node.isAfterWhitespaceInside(paragraph)) return false
    if (paragraph.parents(withSelf = false).none { it.elementType == MarkdownElementTypes.LIST_ITEM }) return true

    val textBefore = paragraph.chars.subSequence(0, node.startOffset - paragraph.startOffset).trim()
    return textBefore.any { it.isFormatterWhitespace() }
  }

  /** Filter out real whitespace blocks from sequence */
  fun filterFromWhitespaces(sequence: Sequence<ASTNode>) = sequence.filter {
    it.elementType !in MarkdownTokenTypeSets.WHITE_SPACES
    // Dirty hack cause for some reason Markdown parser thinks that `>`, `:` are whitespaces
    || (it.elementType in MarkdownTokenTypeSets.WHITE_SPACES && it.text.any { char -> !StringUtil.isWhiteSpace(char) })
  }

  private val linkLikeElements = TokenSet.orSet(
    MarkdownTokenTypeSets.LINKS,
    MarkdownTokenTypeSets.REFERENCE_LINK_SET,
    TokenSet.create(MarkdownElementTypes.IMAGE)
  )
}

private fun ASTNode.isAfterWhitespaceInside(ancestor: ASTNode): Boolean {
  var current = this
  while (current.treePrev == null) {
    current = current.treeParent ?: return false
    if (current == ancestor) return false
  }
  var previous = current.treePrev
  while (previous.lastChildNode != null) {
    previous = previous.lastChildNode
  }
  return previous.chars.lastOrNull()?.isFormatterWhitespace() == true
}
