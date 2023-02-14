package com.intellij.mermaid.lang.formatter

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.mermaid.lang.lexer.MermaidTokenTypeSets
import com.intellij.mermaid.lang.lexer.MermaidTokenTypeSets.DIAGRAM_BODIES
import com.intellij.mermaid.lang.parser.MermaidElements
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.common.AbstractBlock
import com.intellij.psi.formatter.common.SettingsAwareBlock

internal open class MermaidFormattingBlock(
  node: ASTNode,
  private val settings: CodeStyleSettings, private val spacing: SpacingBuilder,
  alignment: Alignment? = null, wrap: Wrap? = null
) : AbstractBlock(node, wrap, alignment), SettingsAwareBlock {

  override fun getSettings(): CodeStyleSettings = settings

  override fun isLeaf(): Boolean = subBlocks.isEmpty()

  override fun getSpacing(child1: Block?, child2: Block): Spacing? {
    return spacing.getSpacing(this, child1, child2)
  }

  override fun getIndent(): Indent? {
    if (node.elementType in DIAGRAM_BODIES) {
      return Indent.getNormalIndent()
    }
    return Indent.getNoneIndent()
  }

  override fun buildChildren(): List<Block> {
    if (node.elementType == MermaidElements.MINDMAP_BODY) {
      return node.children().map { MermaidFormattingBlock(it, settings, spacing) }.toList()
    }
    return filterFromWhitespaces(node.children()).map { MermaidFormattingBlock(it, settings, spacing) }.toList()
  }

  private fun filterFromWhitespaces(sequence: Sequence<ASTNode>) = sequence.filter {
    it.elementType !in MermaidTokenTypeSets.WHITE_SPACES
  }
}

internal fun ASTNode.traverse(withSelf: Boolean, next: (ASTNode) -> ASTNode?): Sequence<ASTNode> {
  return sequence {
    if (withSelf) {
      yield(this@traverse)
    }
    var current = next(this@traverse)
    while (current != null) {
      yield(current)
      current = next(current)
    }
  }
}

internal fun ASTNode.siblings(forward: Boolean, withSelf: Boolean): Sequence<ASTNode> {
  return when {
    forward -> traverse(withSelf) { it.treeNext }
    else -> traverse(withSelf) { it.treePrev }
  }
}

internal fun ASTNode.children(): Sequence<ASTNode> {
  return sequence {
    val first = this@children.firstChildNode ?: return@sequence
    yieldAll(first.siblings(forward = true, withSelf = true))
  }
}
