// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.formatter

import com.intellij.formatting.ASTBlock
import com.intellij.formatting.Alignment
import com.intellij.formatting.Block
import com.intellij.formatting.DependentSpacingRule
import com.intellij.formatting.Indent
import com.intellij.formatting.Spacing
import com.intellij.formatting.SpacingBuilder
import com.intellij.formatting.Wrap
import com.intellij.lang.ASTNode
import com.intellij.mermaid.lang.formatter.settings.MermaidCustomCodeStyleSettings
import com.intellij.mermaid.lang.lexer.MermaidTokenTypeSets
import com.intellij.mermaid.lang.lexer.MermaidTokenTypeSets.DIAGRAM_BODIES_AND_BLOCKS
import com.intellij.mermaid.lang.lexer.MermaidTokenTypeSets.MINDMAP_STATEMENTS
import com.intellij.mermaid.lang.lexer.MermaidTokenTypeSets.STATEMENTS
import com.intellij.mermaid.lang.lexer.MermaidTokens
import com.intellij.mermaid.lang.parser.MermaidElements
import com.intellij.mermaid.lang.psi.children
import com.intellij.mermaid.lang.psi.hasType
import com.intellij.mermaid.lang.psi.siblings
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.common.AbstractBlock
import com.intellij.psi.formatter.common.SettingsAwareBlock
import com.intellij.psi.util.prevLeaf

internal open class MermaidFormattingBlock(
  node: ASTNode,
  private val settings: CodeStyleSettings, private val spacing: SpacingBuilder,
  alignment: Alignment? = null, wrap: Wrap? = null
) : AbstractBlock(node, wrap, alignment), SettingsAwareBlock {

  override fun getSettings(): CodeStyleSettings = settings

  override fun isLeaf(): Boolean = subBlocks.isEmpty()

  override fun getSpacing(child1: Block?, child2: Block): Spacing? {
    return when {
      child1 !is ASTBlock || child2 !is ASTBlock -> null

      child1.node.hasType(MermaidTokens.SEMICOLON) && child2.node.hasType(STATEMENTS) -> {
        val mermaid = settings.getCustomSettings(MermaidCustomCodeStyleSettings::class.java)
        val textRange = TextRange.create(child1.textRange.startOffset, child2.textRange.endOffset)
        val rule = DependentSpacingRule(DependentSpacingRule.Trigger.HAS_LINE_FEEDS).registerData(
          DependentSpacingRule.Anchor.MIN_LINE_FEEDS,
          mermaid.MIN_LINES_BETWEEN_OTHER_STATEMENTS + 1
        )

        Spacing.createDependentLFSpacing(1, 1, textRange, false, mermaid.KEEP_LINES_BETWEEN_OTHER_STATEMENTS, rule)
      }

      else -> spacing.getSpacing(this, child1, child2)
    }
  }

  override fun getIndent(): Indent? {
    if (node.isColonBeforeTaskData()) {
      return Indent.getContinuationIndent()
    }

    if (node.hasType(STATEMENTS) && node.treeParent.hasType(DIAGRAM_BODIES_AND_BLOCKS)) {
      return Indent.getNormalIndent()
    }

    if (node.hasType(MermaidElements.SIMPLE_NOTE_CONTENT)) {
      return Indent.getNormalIndent()
    }

    if (node.hasType(MINDMAP_STATEMENTS)) {
      val prev = node.psi.prevLeaf() as? PsiWhiteSpace ?: return Indent.getNoneIndent()
      return Indent.getSpaceIndent(prev.textLength)
    }

    return Indent.getNoneIndent()
  }

  override fun buildChildren(): List<Block> {
    val children = node.children()

    var alignment: Alignment? = null
    return children.filterFromWhitespaces().map {
      alignment = when {
        it.hasType(MermaidElements.COMPLEX_TASK_NAME) -> Alignment.createAlignment(true, Alignment.Anchor.RIGHT)
        it.isColonBeforeTaskData() -> Alignment.createChildAlignment(alignment)
        else -> null
      }

      MermaidFormattingBlock(it, settings, spacing, alignment)
    }.toList()
  }

  private fun Sequence<ASTNode>.filterFromWhitespaces() = filter {
    it.hasType(MermaidTokenTypeSets.WHITE_SPACES).not()
  }

  private fun ASTNode.isColonBeforeTaskData(): Boolean {
    return hasType(MermaidTokens.COLON) &&
        siblings(forward = true, withSelf = false)
          .filterFromWhitespaces()
          .firstOrNull()?.elementType == MermaidElements.SECTION_TASK_DATA
  }
}
