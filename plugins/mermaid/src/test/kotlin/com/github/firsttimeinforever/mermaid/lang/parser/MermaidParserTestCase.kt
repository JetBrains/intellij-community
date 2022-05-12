package com.github.firsttimeinforever.mermaid.lang.parser

import com.github.firsttimeinforever.mermaid.lang.MermaidParserDefinition
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidLexer
import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilderFactory
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import java.util.*

abstract class MermaidParserTestCase : LightPlatformCodeInsightTestCase() {
  protected fun doTest(content: String, expectedTree: String) {
    val parser = MermaidParser()
    val builder = PsiBuilderFactory.getInstance().createBuilder(MermaidParserDefinition(), MermaidLexer(), content)
    val tree = parser.parse(MermaidParserDefinition.FILE, builder)
    assertEquals(expectedTree, tree.getTreeString())
  }

  /**
   * @return string representation of AST tree
   */
  private fun ASTNode.getTreeString(indent: Int = 0, result: StringJoiner = StringJoiner("\n")): String {
    result.add("${">".repeat(indent)}$this")
    for (child in getChildren(null)) {
      child.getTreeString(indent + 1, result)
    }
    return result.toString()
  }
}
