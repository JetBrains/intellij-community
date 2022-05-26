package com.github.firsttimeinforever.mermaid.lang.lexer

import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ALIAS
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.AS
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.CLOSE_CURLY
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COLON
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COMMA
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COMMENT_TEXT
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
  fun `test simple sequence`() {
    val content = """
    sequenceDiagram
      participant A B as Alice B
      actor J as John
      A B --> J: Hello John, how are you? ; J -->> A B : Great! # And you? J -->> A B
    """.trimIndent()
    val expected = listOf(
      Token(Sequence.SEQUENCE, 0, 15, "sequenceDiagram"),
      Token(EOL, 15, 16, "\n"),
      Token(WHITE_SPACE, 16, 18, "  "),
      Token(Sequence.PARTICIPANT, 18, 29, "participant"),
      Token(WHITE_SPACE, 29, 30, " "),
      Token(ID, 30, 31, "A"),
      Token(WHITE_SPACE, 31, 32, " "),
      Token(ID, 32, 33, "B"),
      Token(WHITE_SPACE, 33, 34, " "),
      Token(AS, 34, 36, "as"),
      Token(WHITE_SPACE, 36, 37, " "),
      Token(ALIAS, 37, 42, "Alice"),
      Token(WHITE_SPACE, 42, 43, " "),
      Token(ALIAS, 43, 44, "B"),
      Token(EOL, 44, 45, "\n"),
      Token(WHITE_SPACE, 45, 47, "  "),
      Token(Sequence.ACTOR, 47, 52, "actor"),
      Token(WHITE_SPACE, 52, 53, " "),
      Token(ID, 53, 54, "J"),
      Token(WHITE_SPACE, 54, 55, " "),
      Token(AS, 55, 57, "as"),
      Token(WHITE_SPACE, 57, 58, " "),
      Token(ALIAS, 58, 62, "John"),
      Token(EOL, 62, 63, "\n"),
      Token(WHITE_SPACE, 63, 65, "  "),
      Token(ID, 65, 66, "A"),
      Token(WHITE_SPACE, 66, 67, " "),
      Token(ID, 67, 68, "B"),
      Token(WHITE_SPACE, 68, 69, " "),
      Token(Sequence.DOTTED_OPEN_ARROW, 69, 72, "-->"),
      Token(WHITE_SPACE, 72, 73, " "),
      Token(ID, 73, 74, "J"),
      Token(COLON, 74, 75, ":"),
      Token(Sequence.MESSAGE, 75, 101, " Hello John, how are you? "),
      Token(SEMICOLON, 101, 102, ";"),
      Token(WHITE_SPACE, 102, 103, " "),
      Token(ID, 103, 104, "J"),
      Token(WHITE_SPACE, 104, 105, " "),
      Token(Sequence.DOTTED_ARROW, 105, 109, "-->>"),
      Token(WHITE_SPACE, 109, 110, " "),
      Token(ID, 110, 111, "A"),
      Token(WHITE_SPACE, 111, 112, " "),
      Token(ID, 112, 113, "B"),
      Token(WHITE_SPACE, 113, 114, " "),
      Token(COLON, 114, 115, ":"),
      Token(Sequence.MESSAGE, 115, 123, " Great! "),
      Token(IGNORED, 123, 144, "# And you? J -->> A B")
    )
    doTest(content, expected)
  }

  fun `test sequence with activations`() {
    val content = """
    sequenceDiagram
      Alice->>John: Hello John, how are you?
      activate John
      John-->>Alice: Great!
      deactivate John
    """.trimIndent()
    val expected = listOf(
      Token(Sequence.SEQUENCE, 0, 15, "sequenceDiagram"),
      Token(EOL, 15, 16, "\n"),
      Token(WHITE_SPACE, 16, 18, "  "),
      Token(ID, 18, 23, "Alice"),
      Token(Sequence.SOLID_ARROW, 23, 26, "->>"),
      Token(ID, 26, 30, "John"),
      Token(COLON, 30, 31, ":"),
      Token(Sequence.MESSAGE, 31, 56, " Hello John, how are you?"),
      Token(EOL, 56, 57, "\n"),
      Token(WHITE_SPACE, 57, 59, "  "),
      Token(Sequence.ACTIVATE, 59, 67, "activate"),
      Token(WHITE_SPACE, 67, 68, " "),
      Token(ID, 68, 72, "John"),
      Token(EOL, 72, 73, "\n"),
      Token(WHITE_SPACE, 73, 75, "  "),
      Token(ID, 75, 79, "John"),
      Token(Sequence.DOTTED_ARROW, 79, 83, "-->>"),
      Token(ID, 83, 88, "Alice"),
      Token(COLON, 88, 89, ":"),
      Token(Sequence.MESSAGE, 89, 96, " Great!"),
      Token(EOL, 96, 97, "\n"),
      Token(WHITE_SPACE, 97, 99, "  "),
      Token(Sequence.DEACTIVATE, 99, 109, "deactivate"),
      Token(WHITE_SPACE, 109, 110, " "),
      Token(ID, 110, 114, "John")
    )
    doTest(content, expected)
  }

  fun `test sequence with short activations`() {
    val content = """
    sequenceDiagram
      Alice->>+John: Hello John, how are you?
      John-->>-Alice: Great!
    """.trimIndent()
    val expected = listOf(
      Token(Sequence.SEQUENCE, 0, 15, "sequenceDiagram"),
      Token(EOL, 15, 16, "\n"),
      Token(WHITE_SPACE, 16, 18, "  "),
      Token(ID, 18, 23, "Alice"),
      Token(Sequence.SOLID_ARROW, 23, 26, "->>"),
      Token(PLUS, 26, 27, "+"),
      Token(ID, 27, 31, "John"),
      Token(COLON, 31, 32, ":"),
      Token(Sequence.MESSAGE, 32, 57, " Hello John, how are you?"),
      Token(EOL, 57, 58, "\n"),
      Token(WHITE_SPACE, 58, 60, "  "),
      Token(ID, 60, 64, "John"),
      Token(Sequence.DOTTED_ARROW, 64, 68, "-->>"),
      Token(MINUS, 68, 69, "-"),
      Token(ID, 69, 74, "Alice"),
      Token(COLON, 74, 75, ":"),
      Token(Sequence.MESSAGE, 75, 82, " Great!")
    )
    doTest(content, expected)
  }

  fun `test sequence with notes`() {
    val content = """
    sequenceDiagram
      participant John
      Note right of John: Text in note
      Alice->John: Hello John, how are you?
      Note over Alice,John: A typical interaction
    """.trimIndent()
    val expected = listOf(
      Token(Sequence.SEQUENCE, 0, 15, "sequenceDiagram"),
      Token(EOL, 15, 16, "\n"),
      Token(WHITE_SPACE, 16, 18, "  "),
      Token(Sequence.PARTICIPANT, 18, 29, "participant"),
      Token(WHITE_SPACE, 29, 30, " "),
      Token(ID, 30, 34, "John"),
      Token(EOL, 34, 35, "\n"),
      Token(WHITE_SPACE, 35, 37, "  "),
      Token(NOTE, 37, 41, "Note"),
      Token(WHITE_SPACE, 41, 42, " "),
      Token(RIGHT_OF, 42, 50, "right of"),
      Token(WHITE_SPACE, 50, 51, " "),
      Token(ID, 51, 55, "John"),
      Token(COLON, 55, 56, ":"),
      Token(Sequence.MESSAGE, 56, 69, " Text in note"),
      Token(EOL, 69, 70, "\n"),
      Token(WHITE_SPACE, 70, 72, "  "),
      Token(ID, 72, 77, "Alice"),
      Token(Sequence.SOLID_OPEN_ARROW, 77, 79, "->"),
      Token(ID, 79, 83, "John"),
      Token(COLON, 83, 84, ":"),
      Token(Sequence.MESSAGE, 84, 109, " Hello John, how are you?"),
      Token(EOL, 109, 110, "\n"),
      Token(WHITE_SPACE, 110, 112, "  "),
      Token(NOTE, 112, 116, "Note"),
      Token(WHITE_SPACE, 116, 117, " "),
      Token(Sequence.OVER, 117, 121, "over"),
      Token(WHITE_SPACE, 121, 122, " "),
      Token(ID, 122, 127, "Alice"),
      Token(COMMA, 127, 128, ","),
      Token(ID, 128, 132, "John"),
      Token(COLON, 132, 133, ":"),
      Token(Sequence.MESSAGE, 133, 155, " A typical interaction")
    )
    doTest(content, expected)
  }

  fun `test sequence with comments`() {
    val content = """
    sequenceDiagram %% this is a comment
      actor Alice %% this is not a comment
      Alice->>John: Hello John, how are you? %% this is not a comment
      %% this is a comment
      John-->>Alice: Great!
    """.trimIndent()
    val expected = listOf(
      Token(Sequence.SEQUENCE, 0, 15, "sequenceDiagram"),
      Token(WHITE_SPACE, 15, 16, " "),
      Token(LINE_COMMENT, 16, 18, "%%"),
      Token(COMMENT_TEXT, 18, 36, " this is a comment"),
      Token(EOL, 36, 37, "\n"),
      Token(WHITE_SPACE, 37, 39, "  "),
      Token(Sequence.ACTOR, 39, 44, "actor"),
      Token(WHITE_SPACE, 44, 45, " "),
      Token(ID, 45, 50, "Alice"),
      Token(WHITE_SPACE, 50, 51, " "),
      Token(ID, 51, 53, "%%"),
      Token(WHITE_SPACE, 53, 54, " "),
      Token(ID, 54, 58, "this"),
      Token(WHITE_SPACE, 58, 59, " "),
      Token(ID, 59, 61, "is"),
      Token(WHITE_SPACE, 61, 62, " "),
      Token(ID, 62, 65, "not"),
      Token(WHITE_SPACE, 65, 66, " "),
      Token(ID, 66, 67, "a"),
      Token(WHITE_SPACE, 67, 68, " "),
      Token(ID, 68, 75, "comment"),
      Token(EOL, 75, 76, "\n"),
      Token(WHITE_SPACE, 76, 78, "  "),
      Token(ID, 78, 83, "Alice"),
      Token(Sequence.SOLID_ARROW, 83, 86, "->>"),
      Token(ID, 86, 90, "John"),
      Token(COLON, 90, 91, ":"),
      Token(Sequence.MESSAGE, 91, 141, " Hello John, how are you? %% this is not a comment"),
      Token(EOL, 141, 142, "\n"),
      Token(WHITE_SPACE, 142, 144, "  "),
      Token(LINE_COMMENT, 144, 146, "%%"),
      Token(COMMENT_TEXT, 146, 164, " this is a comment"),
      Token(EOL, 164, 165, "\n"),
      Token(WHITE_SPACE, 165, 167, "  "),
      Token(ID, 167, 171, "John"),
      Token(Sequence.DOTTED_ARROW, 171, 175, "-->>"),
      Token(ID, 175, 180, "Alice"),
      Token(COLON, 180, 181, ":"),
      Token(Sequence.MESSAGE, 181, 188, " Great!")
    )
    doTest(content, expected)
  }

  fun `test sequence with json formatted link`() {
    val content = """
    sequenceDiagram
      participant Alice
      links Alice: {"Dashboard": "https://dashboard.contoso.com/alice", "Wiki": "https://wiki.contoso.com/alice"}
    """.trimIndent()
    val expected = listOf(
      Token(Sequence.SEQUENCE, 0, 15, "sequenceDiagram"),
      Token(EOL, 15, 16, "\n"),
      Token(WHITE_SPACE, 16, 18, "  "),
      Token(Sequence.PARTICIPANT, 18, 29, "participant"),
      Token(WHITE_SPACE, 29, 30, " "),
      Token(ID, 30, 35, "Alice"),
      Token(EOL, 35, 36, "\n"),
      Token(WHITE_SPACE, 36, 38, "  "),
      Token(Sequence.LINKS, 38, 43, "links"),
      Token(WHITE_SPACE, 43, 44, " "),
      Token(ID, 44, 49, "Alice"),
      Token(COLON, 49, 50, ":"),
      Token(WHITE_SPACE, 50, 51, " "),
      Token(OPEN_CURLY, 51, 52, "{"),
      Token(DOUBLE_QUOTE, 52, 53, "\""),
      Token(STRING_VALUE, 53, 62, "Dashboard"),
      Token(DOUBLE_QUOTE, 62, 63, "\""),
      Token(COLON, 63, 64, ":"),
      Token(WHITE_SPACE, 64, 65, " "),
      Token(DOUBLE_QUOTE, 65, 66, "\""),
      Token(STRING_VALUE, 66, 101, "https://dashboard.contoso.com/alice"),
      Token(DOUBLE_QUOTE, 101, 102, "\""),
      Token(COMMA, 102, 103, ","),
      Token(WHITE_SPACE, 103, 104, " "),
      Token(DOUBLE_QUOTE, 104, 105, "\""),
      Token(STRING_VALUE, 105, 109, "Wiki"),
      Token(DOUBLE_QUOTE, 109, 110, "\""),
      Token(COLON, 110, 111, ":"),
      Token(WHITE_SPACE, 111, 112, " "),
      Token(DOUBLE_QUOTE, 112, 113, "\""),
      Token(STRING_VALUE, 113, 143, "https://wiki.contoso.com/alice"),
      Token(DOUBLE_QUOTE, 143, 144, "\""),
      Token(CLOSE_CURLY, 144, 145, "}")
    )
    doTest(content, expected)
  }

  fun `test sequence with loop`() {
    val content = """
    sequenceDiagram
      Alice->John: Hello John, how are you?
      loop Every minute
          participant Bob; John-->Alice: Great!; Alice -> Bob: WOWO
      end
    """.trimIndent()
    val expected = listOf(
      Token(Sequence.SEQUENCE, 0, 15, "sequenceDiagram"),
      Token(EOL, 15, 16, "\n"),
      Token(WHITE_SPACE, 16, 18, "  "),
      Token(ID, 18, 23, "Alice"),
      Token(Sequence.SOLID_OPEN_ARROW, 23, 25, "->"),
      Token(ID, 25, 29, "John"),
      Token(COLON, 29, 30, ":"),
      Token(Sequence.MESSAGE, 30, 55, " Hello John, how are you?"),
      Token(EOL, 55, 56, "\n"),
      Token(WHITE_SPACE, 56, 58, "  "),
      Token(Sequence.LOOP, 58, 62, "loop"),
      Token(Sequence.MESSAGE, 62, 75, " Every minute"),
      Token(EOL, 75, 76, "\n"),
      Token(WHITE_SPACE, 76, 82, "      "),
      Token(Sequence.PARTICIPANT, 82, 93, "participant"),
      Token(WHITE_SPACE, 93, 94, " "),
      Token(ID, 94, 97, "Bob"),
      Token(SEMICOLON, 97, 98, ";"),
      Token(WHITE_SPACE, 98, 99, " "),
      Token(ID, 99, 103, "John"),
      Token(Sequence.DOTTED_OPEN_ARROW, 103, 106, "-->"),
      Token(ID, 106, 111, "Alice"),
      Token(COLON, 111, 112, ":"),
      Token(Sequence.MESSAGE, 112, 119, " Great!"),
      Token(SEMICOLON, 119, 120, ";"),
      Token(WHITE_SPACE, 120, 121, " "),
      Token(ID, 121, 126, "Alice"),
      Token(WHITE_SPACE, 126, 127, " "),
      Token(Sequence.SOLID_OPEN_ARROW, 127, 129, "->"),
      Token(WHITE_SPACE, 129, 130, " "),
      Token(ID, 130, 133, "Bob"),
      Token(COLON, 133, 134, ":"),
      Token(Sequence.MESSAGE, 134, 139, " WOWO"),
      Token(EOL, 139, 140, "\n"),
      Token(WHITE_SPACE, 140, 142, "  "),
      Token(END, 142, 145, "end")
    )
    doTest(content, expected)
  }

}
