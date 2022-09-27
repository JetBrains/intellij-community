package com.intellij.mermaid.lang.lexer

import com.intellij.mermaid.lang.lexer.MermaidTokens.COLON
import com.intellij.mermaid.lang.lexer.MermaidTokens.DOUBLE_QUOTE
import com.intellij.mermaid.lang.lexer.MermaidTokens.EOL
import com.intellij.mermaid.lang.lexer.MermaidTokens.LINE_COMMENT
import com.intellij.mermaid.lang.lexer.MermaidTokens.Pie
import com.intellij.mermaid.lang.lexer.MermaidTokens.STRING_VALUE
import com.intellij.mermaid.lang.lexer.MermaidTokens.TITLE
import com.intellij.mermaid.lang.lexer.MermaidTokens.TITLE_VALUE
import com.intellij.mermaid.lang.lexer.MermaidTokens.WHITE_SPACE

class PieTest: MermaidLexerTestCase() {
  override val diagramName: String
    get() = "pie"

  fun `test pie with title with newline at the end`() {
    val content = """
    pie
      title Pets will be available
    
    """.trimIndent()
    doTest(content)
  }

  fun `test with value and newlines`() {
    val content = """
    pie
      title Pets adopted by volunteers
    
    
      "Dogs" : 386
    
    """.trimIndent()
    doTest(content)
  }

  fun `test full complex`() {
    val content = """
    pie %% This is comment
      title Pets adopted by volunteers %% This is not comment
      "Dogs" : 386 %% This is comment
      %% This is comment
    """.trimIndent()
    doTest(content)
  }
}
