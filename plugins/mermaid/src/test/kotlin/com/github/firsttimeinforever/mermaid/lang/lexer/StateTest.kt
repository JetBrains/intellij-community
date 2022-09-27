package com.github.firsttimeinforever.mermaid.lang.lexer

import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ANNOTATION_END
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ANNOTATION_START
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ANNOTATION_VALUE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ARROW
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.AS
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.CLOSE_CURLY
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.CLOSE_SQUARE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COLON
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.DIR
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.DIRECTION
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.DOUBLE_QUOTE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.END
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.EOL
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ID
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.LABEL
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.LEFT_OF
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.LINE_COMMENT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.MINUS
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.NOTE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.NOTE_CONTENT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.OPEN_CURLY
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.OPEN_SQUARE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.RIGHT_OF
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.STAR
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.STRING_VALUE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.StateDiagram.STATE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.StateDiagram.STATE_DIAGRAM
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.WHITE_SPACE

class StateTest : MermaidLexerTestCase() {
  override val diagramName: String
    get() = "state"

  fun `test different state definition`() {
    val content = """
    stateDiagram-v2
      s1
      state "This is a state description" as s2
      s2 : This is a state description
    """.trimIndent()
    doTest(content)
  }

  fun `test transitions`() {
    val content = """
    stateDiagram-v2
      s1 --> s2
      s3 --> s4: A transition
    """.trimIndent()
    doTest(content)
  }

  fun `test special start and end states`() {
    val content = """
    stateDiagram-v2
      [*] --> s1
      s1 --> [*]
    """.trimIndent()
    doTest(content)
  }

  fun `test composite states`() {
    val content = """
    stateDiagram-v2
      [*] --> First
      state First {
        [*] --> Second
        state Second {
          [*] --> second
          second --> Third   
        }
        state Third {
          [*] --> third
          third --> [*]
        }
      }
    """.trimIndent()
    doTest(content)
  }

  fun `test state annotation`() {
    val content = """
    stateDiagram-v2
      state if_state <<choice>>
      [*] --> IsPositive
      IsPositive --> if_state
      
      state fork_state <<fork>>
      [*] --> fork_state

      state join_state <<join>>
      join_state --> State4
    """.trimIndent()
    doTest(content)
  }

  fun `test notes`() {
    val content = """
    stateDiagram-v2
      State1: The state with a note
      note right of State1
          Important information! You can write
          notes.
      end note
      State1 --> State2
      note left of State2 : This is the note to the left.
    """.trimIndent()
    doTest(content)
  }

  fun `test diagram with concurrency`() {
    val content = """
    stateDiagram-v2
      [*] --> A
  
      state A {
        [*] --> B
        --
        [*] --> C
        --
        [*] --> D
      }
    """.trimIndent()
    doTest(content)
  }

  fun `test direction`() {
    val content = """
    stateDiagram
      direction LR
      A --> B
      state B {
        direction LR
        a --> b
      }
      B --> D
    """.trimIndent()
    doTest(content)
  }

  fun `test comments`() {
    val content = """
    %% this is a comment
    stateDiagram
      A --> B %% this is a comment
      %% this is a comment
      state B {
        a --> b %% this is a comment
      }
      %% this is a comment
    """.trimIndent()
    doTest(content)
  }
}
