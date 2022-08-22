package org.intellij.plugins.markdown.lang.formatter.blocks.special

import com.intellij.formatting.*
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
  alignment: Alignment?
) : MarkdownWrappingFormattingBlock(settings, spacing, node, alignment, wrap = Wrap.createWrap(WrapType.NORMAL, true)) {
  override fun buildChildren(): List<Block> {
    val noneWrap = Wrap.createWrap(WrapType.NONE, false)
    val filtered = MarkdownBlocks.filterFromWhitespaces(node.children())
    return buildList {
      for (node in filtered) {
        when {
          node.hasType(MarkdownTokenTypes.TEXT) -> processTextElement(this, node, wrap, !node.isFirstContentElement())
          node.hasType(MarkdownTokenTypes.EMPH) -> when {
            node.isLast() -> add(MarkdownWrappingFormattingBlock(settings, spacing, node, alignment, noneWrap))
            else -> add(MarkdownWrappingFormattingBlock(settings, spacing, node, alignment, wrap))
          }
          else -> add(MarkdownBlocks.create(node, settings, spacing) { alignment })
        }
      }
    }
  }

  companion object {
    private fun ASTNode.isLast(): Boolean {
      return treeNext == null
    }

    private fun ASTNode.isFirstContentElement(): Boolean {
      return treePrev?.hasType(MarkdownTokenTypes.EMPH) == true
    }
  }
}
