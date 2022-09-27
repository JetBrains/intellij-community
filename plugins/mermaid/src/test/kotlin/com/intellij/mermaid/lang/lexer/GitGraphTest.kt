package com.intellij.mermaid.lang.lexer

import com.intellij.mermaid.lang.lexer.MermaidTokens.COLON
import com.intellij.mermaid.lang.lexer.MermaidTokens.DOUBLE_QUOTE
import com.intellij.mermaid.lang.lexer.MermaidTokens.EOL
import com.intellij.mermaid.lang.lexer.MermaidTokens.GitGraph.BRANCH
import com.intellij.mermaid.lang.lexer.MermaidTokens.GitGraph.CHECKOUT
import com.intellij.mermaid.lang.lexer.MermaidTokens.GitGraph.CHERRY_PICK
import com.intellij.mermaid.lang.lexer.MermaidTokens.GitGraph.COMMIT
import com.intellij.mermaid.lang.lexer.MermaidTokens.GitGraph.GIT_GRAPH
import com.intellij.mermaid.lang.lexer.MermaidTokens.GitGraph.HIGHLIGHT
import com.intellij.mermaid.lang.lexer.MermaidTokens.GitGraph.MERGE
import com.intellij.mermaid.lang.lexer.MermaidTokens.GitGraph.ORDER
import com.intellij.mermaid.lang.lexer.MermaidTokens.GitGraph.REVERSE
import com.intellij.mermaid.lang.lexer.MermaidTokens.GitGraph.TAG
import com.intellij.mermaid.lang.lexer.MermaidTokens.ID
import com.intellij.mermaid.lang.lexer.MermaidTokens.ID_KEYWORD
import com.intellij.mermaid.lang.lexer.MermaidTokens.NUM
import com.intellij.mermaid.lang.lexer.MermaidTokens.STRING_VALUE
import com.intellij.mermaid.lang.lexer.MermaidTokens.TYPE
import com.intellij.mermaid.lang.lexer.MermaidTokens.WHITE_SPACE

class GitGraphTest : MermaidLexerTestCase() {
  override val diagramName: String
    get() = "gitGraph"

  fun `test simple git graph`() {
    val content = """
    gitGraph
     commit
     commit
     branch develop
     checkout develop
     commit
     commit
     checkout main
     merge develop
     commit
     commit
    """.trimIndent()
    doTest(content)
  }

  fun `test commit id`() {
    val content = """
    gitGraph
      commit id: "Alpha"
    """.trimIndent()
    doTest(content)
  }

  fun `test commit type`() {
    val content = """
    gitGraph
      commit id: "Normal"
      commit id: "Reverse" type: REVERSE
      commit type: HIGHLIGHT
    """.trimIndent()
    doTest(content)
  }

  fun `test commit tags`() {
    val content = """
    gitGraph
      commit tag: "v1.0.0"
      commit id: "Reverse" type: REVERSE tag: "RC_1"
      commit tag: "8.8.4" type: HIGHLIGHT id: "Highlight"
    """.trimIndent()
    doTest(content)
  }

  fun `test cherry pick`() {
    val content = """
    gitGraph
      cherry-pick id : "A"
    """.trimIndent()
    doTest(content)
  }

  fun `test order`() {
    val content = """
    gitGraph
      branch test1 order: 1
    """.trimIndent()
    doTest(content)
  }
}
