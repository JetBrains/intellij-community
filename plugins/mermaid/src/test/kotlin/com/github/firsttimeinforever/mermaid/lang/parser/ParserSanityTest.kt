package com.github.firsttimeinforever.mermaid.lang.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilderFactory
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.github.firsttimeinforever.mermaid.lang.MermaidParserDefinition
import com.github.firsttimeinforever.mermaid.lang.lexer.LexerSanityTest
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidLexer

class ParserSanityTest: LightPlatformCodeInsightTestCase() {
  fun `test stuff`() {
    val content = """
    %%Some
    pie showData title Some title
      "some" : 42
      "some" : 42
    """.trimIndent()
    println(LexerSanityTest.tokensToString(LexerSanityTest.runLexer(content)))
    val parser = MermaidParser()
    val builder = PsiBuilderFactory.getInstance().createBuilder(MermaidParserDefinition(), MermaidLexer(), content)
    val tree = parser.parse(MermaidParserDefinition.FILE, builder)
    printTree(tree)
  }

  private fun printTree(node: ASTNode, indent: Int = 0) {
    println("${">".repeat(indent)}$node")
    for (child in node.getChildren(null)) {
      printTree(child, indent + 1)
    }
  }
}
