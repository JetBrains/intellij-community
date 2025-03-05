package com.jetbrains.python.formatter

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.common.DefaultInjectedLanguageBlockBuilder
import com.jetbrains.python.PythonLanguage

class PyFormattableInjectedBlock(parent: PyBlock,
                                node: ASTNode,
                                alignment: Alignment?,
                                wrap: Wrap?,
                                indent: Indent,
                                settings: CodeStyleSettings,
                                context: PyBlockContext,
                                ): PyBlock(parent, node, alignment, indent, wrap, context), BlockEx {

  private val myBlockBuilder: DefaultInjectedLanguageBlockBuilder = DefaultInjectedLanguageBlockBuilder(settings)

  override fun getSubBlocks(): List<Block?> {
    val result = ArrayList<Block?>()
    myBlockBuilder.addInjectedBlocks(result, node, wrap, alignment, indent)
    return result
  }

  override fun getSpacing(child1: Block?, child2: Block): Spacing? {
    return null
  }

  override fun isLeaf(): Boolean {
    return false
  }

  override fun getLanguage(): Language? {
    return PythonLanguage.getInstance()
  }
}