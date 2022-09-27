package com.github.firsttimeinforever.mermaid.lang.lexer

import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ALIAS
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.AS
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.CLOSE_CURLY
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COLON
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COMMA
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.DOUBLE_QUOTE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.END
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.EOL
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ID
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.IGNORED
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.LINE_COMMENT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.MINUS
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.NOTE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.OPEN_CURLY
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.PLUS
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.RIGHT_OF
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.SEMICOLON
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.STRING_VALUE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Sequence
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.WHITE_SPACE

class SequenceTest : MermaidLexerTestCase() {
  override val diagramName: String
    get() = "sequence"

  fun `test simple sequence`() {
    val content = """
    sequenceDiagram
      participant A B as Alice B
      actor J as John
      A B --> J: Hello John, how are you? ; J -->> A B : Great! # And you? J -->> A B
    """.trimIndent()
    doTest(content)
  }

  fun `test sequence with activations`() {
    val content = """
    sequenceDiagram
      Alice->>John: Hello John, how are you?
      activate John
      John-->>Alice: Great!
      deactivate John
    """.trimIndent()
    doTest(content)
  }

  fun `test sequence with short activations`() {
    val content = """
    sequenceDiagram
      Alice->>+John: Hello John, how are you?
      John-->>-Alice: Great!
    """.trimIndent()
    doTest(content)
  }

  fun `test sequence with notes`() {
    val content = """
    sequenceDiagram
      participant John
      Note right of John: Text in note
      Alice->John: Hello John, how are you?
      Note over Alice,John: A typical interaction
    """.trimIndent()
    doTest(content)
  }

  fun `test sequence with comments`() {
    val content = """
    sequenceDiagram %% this is a comment
      actor Alice %% this is not a comment
      Alice->>John: Hello John, how are you? %% this is not a comment
      %% this is a comment
      John-->>Alice: Great!
    """.trimIndent()
    doTest(content)
  }

  fun `test sequence with json formatted link`() {
    val content = """
    sequenceDiagram
      participant Alice
      links Alice: {"Dashboard": "https://dashboard.contoso.com/alice", "Wiki": "https://wiki.contoso.com/alice"}
    """.trimIndent()
    doTest(content)
  }

  fun `test sequence with loop`() {
    val content = """
    sequenceDiagram
      Alice->John: Hello John, how are you?
      loop Every minute
          participant Bob; John-->Alice: Great!; Alice -> Bob: WOWO
      end
    """.trimIndent()
    doTest(content)
  }
}
