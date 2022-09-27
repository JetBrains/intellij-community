package com.intellij.mermaid.lang.lexer

import com.intellij.mermaid.lang.lexer.MermaidTokens.CLOSE_DIRECTIVE
import com.intellij.mermaid.lang.lexer.MermaidTokens.COLON
import com.intellij.mermaid.lang.lexer.MermaidTokens.COMMA
import com.intellij.mermaid.lang.lexer.MermaidTokens.DIRECTIVE_TEXT
import com.intellij.mermaid.lang.lexer.MermaidTokens.DOUBLE_QUOTE
import com.intellij.mermaid.lang.lexer.MermaidTokens.EOL
import com.intellij.mermaid.lang.lexer.MermaidTokens.LINE_COMMENT
import com.intellij.mermaid.lang.lexer.MermaidTokens.OPEN_DIRECTIVE
import com.intellij.mermaid.lang.lexer.MermaidTokens.STRING_VALUE
import com.intellij.mermaid.lang.lexer.MermaidTokens.WHITE_SPACE

class LexerSanityTest: MermaidLexerTestCase() {
  override val diagramName: String
    get() = "common"

  fun `test line comment`() {
    val content = """
    %% This is comment
    """.trimIndent()
    doTest(content)
  }

  fun `test line comment not eating next newline`() {
    val content = """
    %% This is comment
    
    """.trimIndent()
    doTest(content)
  }

  fun `test empty directive`() {
    val content = """
    %%{}%%
    """.trimIndent()
    doTest(content)
  }

  fun `test empty directive with whitespaces`() {
    val content = """
    %%{    }%%
    """.trimIndent()
    doTest(content)
  }

  fun `test directive with single simple numeric property`() {
    val content = """
    %%{ some: 42 }%%
    """.trimIndent()
    doTest(content)
  }

  fun `test directive with single simple quoted property`() {
    val content = """
    %%{ some: "42" }%%
    """.trimIndent()
    doTest(content)
  }

  fun `test directive with multiple simple properties`() {
    val content = """
    %%{ some: "42", other: 42, more: "value" }%%
    """.trimIndent()
    doTest(content)
  }

  fun `test directive with single simple property and whitespaces and newlines`() {
    val content = """
    %%{   some
      
      
      :
       
       
       42
         
         
         }%%
    """.trimIndent()
    doTest(content)
  }
}
