package org.intellij.plugins.markdown.lang.formatter.blocks.special

import com.intellij.formatting.Alignment
import com.intellij.formatting.Block
import com.intellij.formatting.SpacingBuilder
import com.intellij.formatting.Wrap
import com.intellij.formatting.WrapType
import com.intellij.lang.ASTNode
import com.intellij.psi.codeStyle.CodeStyleSettings
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.formatter.blocks.MarkdownBlocks
import org.intellij.plugins.markdown.lang.psi.util.children
import org.intellij.plugins.markdown.lang.psi.util.hasType

internal class EmphasisFormattingBlock(
  settings: CodeStyleSettings,
  spacing: SpacingBuilder,
  node: ASTNode,
  alignment: Alignment?,
  private val wrap: Wrap? = null
) : MarkdownWrappingFormattingBlock(settings, spacing, node, alignment, wrap = wrap) {
  override fun buildChildren(): List<Block> {
    val contentWrap = Wrap.createWrap(WrapType.NORMAL, true)
    val noneWrap = Wrap.createWrap(WrapType.NONE, false)
    val filtered = MarkdownBlocks.filterFromWhitespaces(node.children())
    return buildList {
      for (node in filtered) {
        when {
          node.hasType(MarkdownTokenTypes.TEXT) -> processTextElement(this, node, contentWrap, !node.isFirstContentElement())
          node.isEmphasisMarker() -> {
            val markerWrap = if (node.isFirst()) wrap ?: contentWrap else noneWrap
            add(MarkdownWrappingFormattingBlock(settings, spacing, node, alignment, markerWrap))
          }
          else -> add(MarkdownBlocks.create(node, settings, spacing) { alignment })
        }
      }
    }
  }
}

private fun ASTNode.isFirst(): Boolean {
  return treePrev == null
}

private fun ASTNode.isFirstContentElement(): Boolean {
  return treePrev?.isEmphasisMarker() == true
}

private fun ASTNode.isEmphasisMarker(): Boolean {
  return hasType(MarkdownTokenTypes.EMPH) || hasType(MarkdownTokenTypes.TILDE)
}