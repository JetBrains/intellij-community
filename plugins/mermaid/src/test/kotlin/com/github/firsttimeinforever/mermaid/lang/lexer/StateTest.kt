package com.github.firsttimeinforever.mermaid.lang.lexer

import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ANNOTATION_END
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ANNOTATION_START
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ANNOTATION_VALUE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ARROW
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.AS
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.CLOSE_CURLY
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.CLOSE_SQUARE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COLON
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COMMENT_TEXT
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
  fun `test different state definition`() {
    val content = """
    stateDiagram-v2
      s1
      state "This is a state description" as s2
      s2 : This is a state description
    """.trimIndent()
    val expected = listOf(
      Token(STATE_DIAGRAM, 0, 15, "stateDiagram-v2"),
      Token(EOL, 15, 16, "\n"),
      Token(WHITE_SPACE, 16, 18, "  "),
      Token(ID, 18, 20, "s1"),
      Token(EOL, 20, 21, "\n"),
      Token(WHITE_SPACE, 21, 23, "  "),
      Token(STATE, 23, 28, "state"),
      Token(WHITE_SPACE, 28, 29, " "),
      Token(DOUBLE_QUOTE, 29, 30, "\""),
      Token(STRING_VALUE, 30, 57, "This is a state description"),
      Token(DOUBLE_QUOTE, 57, 58, "\""),
      Token(WHITE_SPACE, 58, 59, " "),
      Token(AS, 59, 61, "as"),
      Token(WHITE_SPACE, 61, 62, " "),
      Token(ID, 62, 64, "s2"),
      Token(EOL, 64, 65, "\n"),
      Token(WHITE_SPACE, 65, 67, "  "),
      Token(ID, 67, 69, "s2"),
      Token(WHITE_SPACE, 69, 70, " "),
      Token(COLON, 70, 71, ":"),
      Token(LABEL, 71, 99, " This is a state description")
    )
    doTest(content, expected)
  }

  fun `test transitions`() {
    val content = """
    stateDiagram-v2
      s1 --> s2
      s3 --> s4: A transition
    """.trimIndent()
    val expected = listOf(
      Token(STATE_DIAGRAM, 0, 15, "stateDiagram-v2"),
      Token(EOL, 15, 16, "\n"),
      Token(WHITE_SPACE, 16, 18, "  "),
      Token(ID, 18, 20, "s1"),
      Token(WHITE_SPACE, 20, 21, " "),
      Token(ARROW, 21, 24, "-->"),
      Token(WHITE_SPACE, 24, 25, " "),
      Token(ID, 25, 27, "s2"),
      Token(EOL, 27, 28, "\n"),
      Token(WHITE_SPACE, 28, 30, "  "),
      Token(ID, 30, 32, "s3"),
      Token(WHITE_SPACE, 32, 33, " "),
      Token(ARROW, 33, 36, "-->"),
      Token(WHITE_SPACE, 36, 37, " "),
      Token(ID, 37, 39, "s4"),
      Token(COLON, 39, 40, ":"),
      Token(LABEL, 40, 53, " A transition")
    )
    doTest(content, expected)
  }

  fun `test special start and end states`() {
    val content = """
    stateDiagram-v2
      [*] --> s1
      s1 --> [*]
    """.trimIndent()
    val expected = listOf(
      Token(STATE_DIAGRAM, 0, 15, "stateDiagram-v2"),
      Token(EOL, 15, 16, "\n"),
      Token(WHITE_SPACE, 16, 18, "  "),
      Token(OPEN_SQUARE, 18, 19, "["),
      Token(STAR, 19, 20, "*"),
      Token(CLOSE_SQUARE, 20, 21, "]"),
      Token(WHITE_SPACE, 21, 22, " "),
      Token(ARROW, 22, 25, "-->"),
      Token(WHITE_SPACE, 25, 26, " "),
      Token(ID, 26, 28, "s1"),
      Token(EOL, 28, 29, "\n"),
      Token(WHITE_SPACE, 29, 31, "  "),
      Token(ID, 31, 33, "s1"),
      Token(WHITE_SPACE, 33, 34, " "),
      Token(ARROW, 34, 37, "-->"),
      Token(WHITE_SPACE, 37, 38, " "),
      Token(OPEN_SQUARE, 38, 39, "["),
      Token(STAR, 39, 40, "*"),
      Token(CLOSE_SQUARE, 40, 41, "]")
    )
    doTest(content, expected)
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
    val expected = listOf(
      Token(STATE_DIAGRAM, 0, 15, "stateDiagram-v2"),
      Token(EOL, 15, 16, "\n"),
      Token(WHITE_SPACE, 16, 18, "  "),
      Token(OPEN_SQUARE, 18, 19, "["),
      Token(STAR, 19, 20, "*"),
      Token(CLOSE_SQUARE, 20, 21, "]"),
      Token(WHITE_SPACE, 21, 22, " "),
      Token(ARROW, 22, 25, "-->"),
      Token(WHITE_SPACE, 25, 26, " "),
      Token(ID, 26, 31, "First"),
      Token(EOL, 31, 32, "\n"),
      Token(WHITE_SPACE, 32, 34, "  "),
      Token(STATE, 34, 39, "state"),
      Token(WHITE_SPACE, 39, 40, " "),
      Token(ID, 40, 45, "First"),
      Token(WHITE_SPACE, 45, 46, " "),
      Token(OPEN_CURLY, 46, 47, "{"),
      Token(EOL, 47, 48, "\n"),
      Token(WHITE_SPACE, 48, 52, "    "),
      Token(OPEN_SQUARE, 52, 53, "["),
      Token(STAR, 53, 54, "*"),
      Token(CLOSE_SQUARE, 54, 55, "]"),
      Token(WHITE_SPACE, 55, 56, " "),
      Token(ARROW, 56, 59, "-->"),
      Token(WHITE_SPACE, 59, 60, " "),
      Token(ID, 60, 66, "Second"),
      Token(EOL, 66, 67, "\n"),
      Token(WHITE_SPACE, 67, 71, "    "),
      Token(STATE, 71, 76, "state"),
      Token(WHITE_SPACE, 76, 77, " "),
      Token(ID, 77, 83, "Second"),
      Token(WHITE_SPACE, 83, 84, " "),
      Token(OPEN_CURLY, 84, 85, "{"),
      Token(EOL, 85, 86, "\n"),
      Token(WHITE_SPACE, 86, 92, "      "),
      Token(OPEN_SQUARE, 92, 93, "["),
      Token(STAR, 93, 94, "*"),
      Token(CLOSE_SQUARE, 94, 95, "]"),
      Token(WHITE_SPACE, 95, 96, " "),
      Token(ARROW, 96, 99, "-->"),
      Token(WHITE_SPACE, 99, 100, " "),
      Token(ID, 100, 106, "second"),
      Token(EOL, 106, 107, "\n"),
      Token(WHITE_SPACE, 107, 113, "      "),
      Token(ID, 113, 119, "second"),
      Token(WHITE_SPACE, 119, 120, " "),
      Token(ARROW, 120, 123, "-->"),
      Token(WHITE_SPACE, 123, 124, " "),
      Token(ID, 124, 129, "Third"),
      Token(WHITE_SPACE, 129, 132, "   "),
      Token(EOL, 132, 133, "\n"),
      Token(WHITE_SPACE, 133, 137, "    "),
      Token(CLOSE_CURLY, 137, 138, "}"),
      Token(EOL, 138, 139, "\n"),
      Token(WHITE_SPACE, 139, 143, "    "),
      Token(STATE, 143, 148, "state"),
      Token(WHITE_SPACE, 148, 149, " "),
      Token(ID, 149, 154, "Third"),
      Token(WHITE_SPACE, 154, 155, " "),
      Token(OPEN_CURLY, 155, 156, "{"),
      Token(EOL, 156, 157, "\n"),
      Token(WHITE_SPACE, 157, 163, "      "),
      Token(OPEN_SQUARE, 163, 164, "["),
      Token(STAR, 164, 165, "*"),
      Token(CLOSE_SQUARE, 165, 166, "]"),
      Token(WHITE_SPACE, 166, 167, " "),
      Token(ARROW, 167, 170, "-->"),
      Token(WHITE_SPACE, 170, 171, " "),
      Token(ID, 171, 176, "third"),
      Token(EOL, 176, 177, "\n"),
      Token(WHITE_SPACE, 177, 183, "      "),
      Token(ID, 183, 188, "third"),
      Token(WHITE_SPACE, 188, 189, " "),
      Token(ARROW, 189, 192, "-->"),
      Token(WHITE_SPACE, 192, 193, " "),
      Token(OPEN_SQUARE, 193, 194, "["),
      Token(STAR, 194, 195, "*"),
      Token(CLOSE_SQUARE, 195, 196, "]"),
      Token(EOL, 196, 197, "\n"),
      Token(WHITE_SPACE, 197, 201, "    "),
      Token(CLOSE_CURLY, 201, 202, "}"),
      Token(EOL, 202, 203, "\n"),
      Token(WHITE_SPACE, 203, 205, "  "),
      Token(CLOSE_CURLY, 205, 206, "}")
    )
    doTest(content, expected)
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
    val expected = listOf(
      Token(STATE_DIAGRAM, 0, 15, "stateDiagram-v2"),
      Token(EOL, 15, 16, "\n"),
      Token(WHITE_SPACE, 16, 18, "  "),
      Token(STATE, 18, 23, "state"),
      Token(WHITE_SPACE, 23, 24, " "),
      Token(ID, 24, 32, "if_state"),
      Token(WHITE_SPACE, 32, 33, " "),
      Token(ANNOTATION_START, 33, 35, "<<"),
      Token(ANNOTATION_VALUE, 35, 41, "choice"),
      Token(ANNOTATION_END, 41, 43, ">>"),
      Token(EOL, 43, 44, "\n"),
      Token(WHITE_SPACE, 44, 46, "  "),
      Token(OPEN_SQUARE, 46, 47, "["),
      Token(STAR, 47, 48, "*"),
      Token(CLOSE_SQUARE, 48, 49, "]"),
      Token(WHITE_SPACE, 49, 50, " "),
      Token(ARROW, 50, 53, "-->"),
      Token(WHITE_SPACE, 53, 54, " "),
      Token(ID, 54, 64, "IsPositive"),
      Token(EOL, 64, 65, "\n"),
      Token(WHITE_SPACE, 65, 67, "  "),
      Token(ID, 67, 77, "IsPositive"),
      Token(WHITE_SPACE, 77, 78, " "),
      Token(ARROW, 78, 81, "-->"),
      Token(WHITE_SPACE, 81, 82, " "),
      Token(ID, 82, 90, "if_state"),
      Token(EOL, 90, 91, "\n"),
      Token(WHITE_SPACE, 91, 93, "  "),
      Token(EOL, 93, 94, "\n"),
      Token(WHITE_SPACE, 94, 96, "  "),
      Token(STATE, 96, 101, "state"),
      Token(WHITE_SPACE, 101, 102, " "),
      Token(ID, 102, 112, "fork_state"),
      Token(WHITE_SPACE, 112, 113, " "),
      Token(ANNOTATION_START, 113, 115, "<<"),
      Token(ANNOTATION_VALUE, 115, 119, "fork"),
      Token(ANNOTATION_END, 119, 121, ">>"),
      Token(EOL, 121, 122, "\n"),
      Token(WHITE_SPACE, 122, 124, "  "),
      Token(OPEN_SQUARE, 124, 125, "["),
      Token(STAR, 125, 126, "*"),
      Token(CLOSE_SQUARE, 126, 127, "]"),
      Token(WHITE_SPACE, 127, 128, " "),
      Token(ARROW, 128, 131, "-->"),
      Token(WHITE_SPACE, 131, 132, " "),
      Token(ID, 132, 142, "fork_state"),
      Token(EOL, 142, 144, "\n"),
      Token(WHITE_SPACE, 144, 146, "  "),
      Token(STATE, 146, 151, "state"),
      Token(WHITE_SPACE, 151, 152, " "),
      Token(ID, 152, 162, "join_state"),
      Token(WHITE_SPACE, 162, 163, " "),
      Token(ANNOTATION_START, 163, 165, "<<"),
      Token(ANNOTATION_VALUE, 165, 169, "join"),
      Token(ANNOTATION_END, 169, 171, ">>"),
      Token(EOL, 171, 172, "\n"),
      Token(WHITE_SPACE, 172, 174, "  "),
      Token(ID, 174, 184, "join_state"),
      Token(WHITE_SPACE, 184, 185, " "),
      Token(ARROW, 185, 188, "-->"),
      Token(WHITE_SPACE, 188, 189, " "),
      Token(ID, 189, 195, "State4")
    )
    doTest(content, expected)
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
    val expected = listOf(
      Token(STATE_DIAGRAM, 0, 15, "stateDiagram-v2"),
      Token(EOL, 15, 16, "\n"),
      Token(WHITE_SPACE, 16, 18, "  "),
      Token(ID, 18, 24, "State1"),
      Token(COLON, 24, 25, ":"),
      Token(LABEL, 25, 47, " The state with a note"),
      Token(EOL, 47, 48, "\n"),
      Token(WHITE_SPACE, 48, 50, "  "),
      Token(NOTE, 50, 54, "note"),
      Token(WHITE_SPACE, 54, 55, " "),
      Token(RIGHT_OF, 55, 63, "right of"),
      Token(WHITE_SPACE, 63, 64, " "),
      Token(ID, 64, 70, "State1"),
      Token(EOL, 70, 71, "\n"),
      Token(WHITE_SPACE, 71, 77, "      "),
      Token(NOTE_CONTENT, 77, 113, "Important information! You can write"),
      Token(EOL, 113, 114, "\n"),
      Token(WHITE_SPACE, 114, 120, "      "),
      Token(NOTE_CONTENT, 120, 126, "notes."),
      Token(EOL, 126, 127, "\n"),
      Token(WHITE_SPACE, 127, 129, "  "),
      Token(END, 129, 137, "end note"),
      Token(EOL, 137, 138, "\n"),
      Token(WHITE_SPACE, 138, 140, "  "),
      Token(ID, 140, 146, "State1"),
      Token(WHITE_SPACE, 146, 147, " "),
      Token(ARROW, 147, 150, "-->"),
      Token(WHITE_SPACE, 150, 151, " "),
      Token(ID, 151, 157, "State2"),
      Token(EOL, 157, 158, "\n"),
      Token(WHITE_SPACE, 158, 160, "  "),
      Token(NOTE, 160, 164, "note"),
      Token(WHITE_SPACE, 164, 165, " "),
      Token(LEFT_OF, 165, 172, "left of"),
      Token(WHITE_SPACE, 172, 173, " "),
      Token(ID, 173, 179, "State2"),
      Token(WHITE_SPACE, 179, 180, " "),
      Token(COLON, 180, 181, ":"),
      Token(WHITE_SPACE, 181, 182, " "),
      Token(NOTE_CONTENT, 182, 211, "This is the note to the left.")
    )
    doTest(content, expected)
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
    val expected = listOf(
      Token(STATE_DIAGRAM, 0, 15, "stateDiagram-v2"),
      Token(EOL, 15, 16, "\n"),
      Token(WHITE_SPACE, 16, 18, "  "),
      Token(OPEN_SQUARE, 18, 19, "["),
      Token(STAR, 19, 20, "*"),
      Token(CLOSE_SQUARE, 20, 21, "]"),
      Token(WHITE_SPACE, 21, 22, " "),
      Token(ARROW, 22, 25, "-->"),
      Token(WHITE_SPACE, 25, 26, " "),
      Token(ID, 26, 27, "A"),
      Token(EOL, 27, 29, "\n"),
      Token(WHITE_SPACE, 29, 31, "  "),
      Token(STATE, 31, 36, "state"),
      Token(WHITE_SPACE, 36, 37, " "),
      Token(ID, 37, 38, "A"),
      Token(WHITE_SPACE, 38, 39, " "),
      Token(OPEN_CURLY, 39, 40, "{"),
      Token(EOL, 40, 41, "\n"),
      Token(WHITE_SPACE, 41, 45, "    "),
      Token(OPEN_SQUARE, 45, 46, "["),
      Token(STAR, 46, 47, "*"),
      Token(CLOSE_SQUARE, 47, 48, "]"),
      Token(WHITE_SPACE, 48, 49, " "),
      Token(ARROW, 49, 52, "-->"),
      Token(WHITE_SPACE, 52, 53, " "),
      Token(ID, 53, 54, "B"),
      Token(EOL, 54, 55, "\n"),
      Token(WHITE_SPACE, 55, 59, "    "),
      Token(MINUS, 59, 60, "-"),
      Token(MINUS, 60, 61, "-"),
      Token(EOL, 61, 62, "\n"),
      Token(WHITE_SPACE, 62, 66, "    "),
      Token(OPEN_SQUARE, 66, 67, "["),
      Token(STAR, 67, 68, "*"),
      Token(CLOSE_SQUARE, 68, 69, "]"),
      Token(WHITE_SPACE, 69, 70, " "),
      Token(ARROW, 70, 73, "-->"),
      Token(WHITE_SPACE, 73, 74, " "),
      Token(ID, 74, 75, "C"),
      Token(EOL, 75, 76, "\n"),
      Token(WHITE_SPACE, 76, 80, "    "),
      Token(MINUS, 80, 81, "-"),
      Token(MINUS, 81, 82, "-"),
      Token(EOL, 82, 83, "\n"),
      Token(WHITE_SPACE, 83, 87, "    "),
      Token(OPEN_SQUARE, 87, 88, "["),
      Token(STAR, 88, 89, "*"),
      Token(CLOSE_SQUARE, 89, 90, "]"),
      Token(WHITE_SPACE, 90, 91, " "),
      Token(ARROW, 91, 94, "-->"),
      Token(WHITE_SPACE, 94, 95, " "),
      Token(ID, 95, 96, "D"),
      Token(EOL, 96, 97, "\n"),
      Token(WHITE_SPACE, 97, 99, "  "),
      Token(CLOSE_CURLY, 99, 100, "}")
    )
    doTest(content, expected)
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
    val expected = listOf(
      Token(STATE_DIAGRAM, 0, 12, "stateDiagram"),
      Token(EOL, 12, 13, "\n"),
      Token(WHITE_SPACE, 13, 15, "  "),
      Token(DIRECTION, 15, 24, "direction"),
      Token(WHITE_SPACE, 24, 25, " "),
      Token(DIR, 25, 27, "LR"),
      Token(EOL, 27, 28, "\n"),
      Token(WHITE_SPACE, 28, 30, "  "),
      Token(ID, 30, 31, "A"),
      Token(WHITE_SPACE, 31, 32, " "),
      Token(ARROW, 32, 35, "-->"),
      Token(WHITE_SPACE, 35, 36, " "),
      Token(ID, 36, 37, "B"),
      Token(EOL, 37, 38, "\n"),
      Token(WHITE_SPACE, 38, 40, "  "),
      Token(STATE, 40, 45, "state"),
      Token(WHITE_SPACE, 45, 46, " "),
      Token(ID, 46, 47, "B"),
      Token(WHITE_SPACE, 47, 48, " "),
      Token(OPEN_CURLY, 48, 49, "{"),
      Token(EOL, 49, 50, "\n"),
      Token(WHITE_SPACE, 50, 54, "    "),
      Token(DIRECTION, 54, 63, "direction"),
      Token(WHITE_SPACE, 63, 64, " "),
      Token(DIR, 64, 66, "LR"),
      Token(EOL, 66, 67, "\n"),
      Token(WHITE_SPACE, 67, 71, "    "),
      Token(ID, 71, 72, "a"),
      Token(WHITE_SPACE, 72, 73, " "),
      Token(ARROW, 73, 76, "-->"),
      Token(WHITE_SPACE, 76, 77, " "),
      Token(ID, 77, 78, "b"),
      Token(EOL, 78, 79, "\n"),
      Token(WHITE_SPACE, 79, 81, "  "),
      Token(CLOSE_CURLY, 81, 82, "}"),
      Token(EOL, 82, 83, "\n"),
      Token(WHITE_SPACE, 83, 85, "  "),
      Token(ID, 85, 86, "B"),
      Token(WHITE_SPACE, 86, 87, " "),
      Token(ARROW, 87, 90, "-->"),
      Token(WHITE_SPACE, 90, 91, " "),
      Token(ID, 91, 92, "D")
    )
    doTest(content, expected)
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
    val expected = listOf(
      Token(LINE_COMMENT, 0, 2, "%%"),
      Token(COMMENT_TEXT, 2, 20, " this is a comment"),
      Token(EOL, 20, 21, "\n"),
      Token(STATE_DIAGRAM, 21, 33, "stateDiagram"),
      Token(EOL, 33, 34, "\n"),
      Token(WHITE_SPACE, 34, 36, "  "),
      Token(ID, 36, 37, "A"),
      Token(WHITE_SPACE, 37, 38, " "),
      Token(ARROW, 38, 41, "-->"),
      Token(WHITE_SPACE, 41, 42, " "),
      Token(ID, 42, 43, "B"),
      Token(WHITE_SPACE, 43, 44, " "),
      Token(LINE_COMMENT, 44, 46, "%%"),
      Token(COMMENT_TEXT, 46, 64, " this is a comment"),
      Token(EOL, 64, 65, "\n"),
      Token(WHITE_SPACE, 65, 67, "  "),
      Token(LINE_COMMENT, 67, 69, "%%"),
      Token(COMMENT_TEXT, 69, 87, " this is a comment"),
      Token(EOL, 87, 88, "\n"),
      Token(WHITE_SPACE, 88, 90, "  "),
      Token(STATE, 90, 95, "state"),
      Token(WHITE_SPACE, 95, 96, " "),
      Token(ID, 96, 97, "B"),
      Token(WHITE_SPACE, 97, 98, " "),
      Token(OPEN_CURLY, 98, 99, "{"),
      Token(EOL, 99, 100, "\n"),
      Token(WHITE_SPACE, 100, 104, "    "),
      Token(ID, 104, 105, "a"),
      Token(WHITE_SPACE, 105, 106, " "),
      Token(ARROW, 106, 109, "-->"),
      Token(WHITE_SPACE, 109, 110, " "),
      Token(ID, 110, 111, "b"),
      Token(WHITE_SPACE, 111, 112, " "),
      Token(LINE_COMMENT, 112, 114, "%%"),
      Token(COMMENT_TEXT, 114, 132, " this is a comment"),
      Token(EOL, 132, 133, "\n"),
      Token(WHITE_SPACE, 133, 135, "  "),
      Token(CLOSE_CURLY, 135, 136, "}"),
      Token(EOL, 136, 137, "\n"),
      Token(WHITE_SPACE, 137, 139, "  "),
      Token(LINE_COMMENT, 139, 141, "%%"),
      Token(COMMENT_TEXT, 141, 159, " this is a comment")
    )
    doTest(content, expected)
  }
}
