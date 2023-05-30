package com.intellij.mermaid.lang.formatter

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.mermaid.lang.lexer.MermaidTokenTypeSets
import com.intellij.mermaid.lang.lexer.MermaidTokenTypeSets.DIAGRAM_BODIES_AND_BLOCKS
import com.intellij.mermaid.lang.lexer.MermaidTokenTypeSets.STATEMENTS
import com.intellij.mermaid.lang.lexer.MermaidTokens
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
    if (node.isColonBeforeTaskData()) {
      return Indent.getContinuationIndent()
    }

    if (node.elementType in STATEMENTS && node.treeParent.elementType in DIAGRAM_BODIES_AND_BLOCKS) {
      return Indent.getNormalIndent()
    }

    return Indent.getNoneIndent()
  }

  override fun buildChildren(): List<Block> {
    val children = node.children()

    if ((children.filter { it.elementType == MermaidElements.MINDMAP_HEADER }
        .any() || node.elementType == MermaidElements.MINDMAP_BODY) ||
      (children.filter { it.elementType == MermaidElements.QUADRANT_HEADER }
        .any() || node.elementType == MermaidElements.QUADRANT_BODY)
    ) {
      return children.map { MermaidFormattingBlock(it, settings, spacing) }.toList()
    }

    var alignment: Alignment? = null
    return children.filterFromWhitespaces().map {
      alignment = when {
        it.elementType == MermaidElements.COMPLEX_TASK_NAME -> Alignment.createAlignment(true, Alignment.Anchor.RIGHT)
        it.isColonBeforeTaskData() -> Alignment.createChildAlignment(alignment)
        else -> null
      }

      MermaidFormattingBlock(it, settings, spacing, alignment)
    }.toList()
  }

  private fun Sequence<ASTNode>.filterFromWhitespaces() = filter {
    it.elementType !in MermaidTokenTypeSets.WHITE_SPACES
  }

  private fun ASTNode.isColonBeforeTaskData(): Boolean {
    return elementType == MermaidTokens.COLON &&
      siblings(forward = true, withSelf = false)
        .filterFromWhitespaces()
        .firstOrNull()?.elementType == MermaidElements.SECTION_TASK_DATA
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
