package com.github.firsttimeinforever.mermaid.lang.lexer

import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COLON
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.DOUBLE_QUOTE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.EOL
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.GitGraph.BRANCH
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.GitGraph.CHECKOUT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.GitGraph.CHERRY_PICK
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.GitGraph.COMMIT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.GitGraph.GIT_GRAPH
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.GitGraph.HIGHLIGHT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.GitGraph.MERGE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.GitGraph.ORDER
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.GitGraph.REVERSE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.GitGraph.TAG
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ID
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ID_KEYWORD
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.NUM
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.STRING_VALUE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.TYPE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.WHITE_SPACE

class GitGraphTest : MermaidLexerTestCase() {
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
    val expected = listOf(
      Token(GIT_GRAPH, 0, 8, "gitGraph"),
      Token(EOL, 8, 9, "\n"),
      Token(WHITE_SPACE, 9, 10, " "),
      Token(COMMIT, 10, 16, "commit"),
      Token(EOL, 16, 17, "\n"),
      Token(WHITE_SPACE, 17, 18, " "),
      Token(COMMIT, 18, 24, "commit"),
      Token(EOL, 24, 25, "\n"),
      Token(WHITE_SPACE, 25, 26, " "),
      Token(BRANCH, 26, 32, "branch"),
      Token(WHITE_SPACE, 32, 33, " "),
      Token(ID, 33, 40, "develop"),
      Token(EOL, 40, 41, "\n"),
      Token(WHITE_SPACE, 41, 42, " "),
      Token(CHECKOUT, 42, 50, "checkout"),
      Token(WHITE_SPACE, 50, 51, " "),
      Token(ID, 51, 58, "develop"),
      Token(EOL, 58, 59, "\n"),
      Token(WHITE_SPACE, 59, 60, " "),
      Token(COMMIT, 60, 66, "commit"),
      Token(EOL, 66, 67, "\n"),
      Token(WHITE_SPACE, 67, 68, " "),
      Token(COMMIT, 68, 74, "commit"),
      Token(EOL, 74, 75, "\n"),
      Token(WHITE_SPACE, 75, 76, " "),
      Token(CHECKOUT, 76, 84, "checkout"),
      Token(WHITE_SPACE, 84, 85, " "),
      Token(ID, 85, 89, "main"),
      Token(EOL, 89, 90, "\n"),
      Token(WHITE_SPACE, 90, 91, " "),
      Token(MERGE, 91, 96, "merge"),
      Token(WHITE_SPACE, 96, 97, " "),
      Token(ID, 97, 104, "develop"),
      Token(EOL, 104, 105, "\n"),
      Token(WHITE_SPACE, 105, 106, " "),
      Token(COMMIT, 106, 112, "commit"),
      Token(EOL, 112, 113, "\n"),
      Token(WHITE_SPACE, 113, 114, " "),
      Token(COMMIT, 114, 120, "commit")
    )
    doTest(content, expected)
  }

  fun `test commit id`() {
    val content = """
    gitGraph
      commit id: "Alpha"
    """.trimIndent()
    val expected = listOf(
      Token(GIT_GRAPH, 0, 8, "gitGraph"),
      Token(EOL, 8, 9, "\n"),
      Token(WHITE_SPACE, 9, 11, "  "),
      Token(COMMIT, 11, 17, "commit"),
      Token(WHITE_SPACE, 17, 18, " "),
      Token(ID_KEYWORD, 18, 20, "id"),
      Token(COLON, 20, 21, ":"),
      Token(WHITE_SPACE, 21, 22, " "),
      Token(DOUBLE_QUOTE, 22, 23, "\""),
      Token(STRING_VALUE, 23, 28, "Alpha"),
      Token(DOUBLE_QUOTE, 28, 29, "\"")
    )
    doTest(content, expected)
  }

  fun `test commit type`() {
    val content = """
    gitGraph
      commit id: "Normal"
      commit id: "Reverse" type: REVERSE
      commit type: HIGHLIGHT
    """.trimIndent()
    val expected = listOf(
      Token(GIT_GRAPH, 0, 8, "gitGraph"),
      Token(EOL, 8, 9, "\n"),
      Token(WHITE_SPACE, 9, 11, "  "),
      Token(COMMIT, 11, 17, "commit"),
      Token(WHITE_SPACE, 17, 18, " "),
      Token(ID_KEYWORD, 18, 20, "id"),
      Token(COLON, 20, 21, ":"),
      Token(WHITE_SPACE, 21, 22, " "),
      Token(DOUBLE_QUOTE, 22, 23, "\""),
      Token(STRING_VALUE, 23, 29, "Normal"),
      Token(DOUBLE_QUOTE, 29, 30, "\""),
      Token(EOL, 30, 31, "\n"),
      Token(WHITE_SPACE, 31, 33, "  "),
      Token(COMMIT, 33, 39, "commit"),
      Token(WHITE_SPACE, 39, 40, " "),
      Token(ID_KEYWORD, 40, 42, "id"),
      Token(COLON, 42, 43, ":"),
      Token(WHITE_SPACE, 43, 44, " "),
      Token(DOUBLE_QUOTE, 44, 45, "\""),
      Token(STRING_VALUE, 45, 52, "Reverse"),
      Token(DOUBLE_QUOTE, 52, 53, "\""),
      Token(WHITE_SPACE, 53, 54, " "),
      Token(TYPE, 54, 58, "type"),
      Token(COLON, 58, 59, ":"),
      Token(WHITE_SPACE, 59, 60, " "),
      Token(REVERSE, 60, 67, "REVERSE"),
      Token(EOL, 67, 68, "\n"),
      Token(WHITE_SPACE, 68, 70, "  "),
      Token(COMMIT, 70, 76, "commit"),
      Token(WHITE_SPACE, 76, 77, " "),
      Token(TYPE, 77, 81, "type"),
      Token(COLON, 81, 82, ":"),
      Token(WHITE_SPACE, 82, 83, " "),
      Token(HIGHLIGHT, 83, 92, "HIGHLIGHT")
    )
    doTest(content, expected)
  }

  fun `test commit tags`() {
    val content = """
    gitGraph
      commit tag: "v1.0.0"
      commit id: "Reverse" type: REVERSE tag: "RC_1"
      commit tag: "8.8.4" type: HIGHLIGHT id: "Highlight"
    """.trimIndent()
    val expected = listOf(
      Token(GIT_GRAPH, 0, 8, "gitGraph"),
      Token(EOL, 8, 9, "\n"),
      Token(WHITE_SPACE, 9, 11, "  "),
      Token(COMMIT, 11, 17, "commit"),
      Token(WHITE_SPACE, 17, 18, " "),
      Token(TAG, 18, 21, "tag"),
      Token(COLON, 21, 22, ":"),
      Token(WHITE_SPACE, 22, 23, " "),
      Token(DOUBLE_QUOTE, 23, 24, "\""),
      Token(STRING_VALUE, 24, 30, "v1.0.0"),
      Token(DOUBLE_QUOTE, 30, 31, "\""),
      Token(EOL, 31, 32, "\n"),
      Token(WHITE_SPACE, 32, 34, "  "),
      Token(COMMIT, 34, 40, "commit"),
      Token(WHITE_SPACE, 40, 41, " "),
      Token(ID_KEYWORD, 41, 43, "id"),
      Token(COLON, 43, 44, ":"),
      Token(WHITE_SPACE, 44, 45, " "),
      Token(DOUBLE_QUOTE, 45, 46, "\""),
      Token(STRING_VALUE, 46, 53, "Reverse"),
      Token(DOUBLE_QUOTE, 53, 54, "\""),
      Token(WHITE_SPACE, 54, 55, " "),
      Token(TYPE, 55, 59, "type"),
      Token(COLON, 59, 60, ":"),
      Token(WHITE_SPACE, 60, 61, " "),
      Token(REVERSE, 61, 68, "REVERSE"),
      Token(WHITE_SPACE, 68, 69, " "),
      Token(TAG, 69, 72, "tag"),
      Token(COLON, 72, 73, ":"),
      Token(WHITE_SPACE, 73, 74, " "),
      Token(DOUBLE_QUOTE, 74, 75, "\""),
      Token(STRING_VALUE, 75, 79, "RC_1"),
      Token(DOUBLE_QUOTE, 79, 80, "\""),
      Token(EOL, 80, 81, "\n"),
      Token(WHITE_SPACE, 81, 83, "  "),
      Token(COMMIT, 83, 89, "commit"),
      Token(WHITE_SPACE, 89, 90, " "),
      Token(TAG, 90, 93, "tag"),
      Token(COLON, 93, 94, ":"),
      Token(WHITE_SPACE, 94, 95, " "),
      Token(DOUBLE_QUOTE, 95, 96, "\""),
      Token(STRING_VALUE, 96, 101, "8.8.4"),
      Token(DOUBLE_QUOTE, 101, 102, "\""),
      Token(WHITE_SPACE, 102, 103, " "),
      Token(TYPE, 103, 107, "type"),
      Token(COLON, 107, 108, ":"),
      Token(WHITE_SPACE, 108, 109, " "),
      Token(HIGHLIGHT, 109, 118, "HIGHLIGHT"),
      Token(WHITE_SPACE, 118, 119, " "),
      Token(ID_KEYWORD, 119, 121, "id"),
      Token(COLON, 121, 122, ":"),
      Token(WHITE_SPACE, 122, 123, " "),
      Token(DOUBLE_QUOTE, 123, 124, "\""),
      Token(STRING_VALUE, 124, 133, "Highlight"),
      Token(DOUBLE_QUOTE, 133, 134, "\"")
    )
    doTest(content, expected)
  }

  fun `test cherry pick`() {
    val content = """
    gitGraph
      cherry-pick id : "A"
    """.trimIndent()
    val expected = listOf(
      Token(GIT_GRAPH, 0, 8, "gitGraph"),
      Token(EOL, 8, 9, "\n"),
      Token(WHITE_SPACE, 9, 11, "  "),
      Token(CHERRY_PICK, 11, 22, "cherry-pick"),
      Token(WHITE_SPACE, 22, 23, " "),
      Token(ID_KEYWORD, 23, 25, "id"),
      Token(WHITE_SPACE, 25, 26, " "),
      Token(COLON, 26, 27, ":"),
      Token(WHITE_SPACE, 27, 28, " "),
      Token(DOUBLE_QUOTE, 28, 29, "\""),
      Token(STRING_VALUE, 29, 30, "A"),
      Token(DOUBLE_QUOTE, 30, 31, "\"")
    )
    doTest(content, expected)
  }

  fun `test order`() {
    val content = """
    gitGraph
      branch test1 order: 1
    """.trimIndent()
    val expected = listOf(
      Token(GIT_GRAPH, 0, 8, "gitGraph"),
      Token(EOL, 8, 9, "\n"),
      Token(WHITE_SPACE, 9, 11, "  "),
      Token(BRANCH, 11, 17, "branch"),
      Token(WHITE_SPACE, 17, 18, " "),
      Token(ID, 18, 23, "test1"),
      Token(WHITE_SPACE, 23, 24, " "),
      Token(ORDER, 24, 29, "order"),
      Token(COLON, 29, 30, ":"),
      Token(WHITE_SPACE, 30, 31, " "),
      Token(NUM, 31, 32, "1")
    )
    doTest(content, expected)
  }
}
