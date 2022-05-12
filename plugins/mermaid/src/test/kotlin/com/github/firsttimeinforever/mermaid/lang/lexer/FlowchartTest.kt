package com.github.firsttimeinforever.mermaid.lang.lexer

import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.DOUBLE_QUOTE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.EOL
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Flowchart
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Flowchart.STYLE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Flowchart.STYLE_OPT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Flowchart.SUBGRAPH
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Flowchart.END
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Flowchart.DIRECTION
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Flowchart.STYLE_TARGET
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Flowchart.STYLE_VAL
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Flowchart.LINK_STYLE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.SEMICOLON
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COMMA
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COLON
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COMMENT_TEXT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Flowchart.CLASS
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Flowchart.CLASS_DEF
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Flowchart.STYLE_SEPARATOR
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.LINE_COMMENT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.WHITE_SPACE

class FlowchartTest: MermaidLexerTestCase() {
  fun `test simple flowchart`() {
    val content = """
    flowchart TD
      Start --> Stop
    """.trimIndent()
    val expected = listOf(
      Token(Flowchart.FLOWCHART, 0, 9, "flowchart"),
      Token(WHITE_SPACE, 9, 10, " "),
      Token(Flowchart.DIR, 10, 12, "TD"),
      Token(EOL, 12, 13, "\n"),
      Token(WHITE_SPACE, 13, 15, "  "),
      Token(Flowchart.NODE_ID, 15, 20, "Start"),
      Token(WHITE_SPACE, 20, 21, " "),
      Token(Flowchart.LINK, 21, 24, "-->"),
      Token(WHITE_SPACE, 24, 25, " "),
      Token(Flowchart.NODE_ID, 25, 29, "Stop")
      )
    doTest(content, expected)
  }

  fun `test flowchart with named nodes`() {
    val content = """
    flowchart TD
      id1[Start] --> id2["Stop"]
    """.trimIndent()
    val expected = listOf(
      Token(Flowchart.FLOWCHART, 0, 9, "flowchart"),
      Token(WHITE_SPACE, 9, 10, " "),
      Token(Flowchart.DIR, 10, 12, "TD"),
      Token(EOL, 12, 13, "\n"),
      Token(WHITE_SPACE, 13, 15, "  "),
      Token(Flowchart.NODE_ID, 15, 18, "id1"),
      Token(Flowchart.SQUARE_START, 18, 19, "["),
      Token(Flowchart.NODE_TEXT, 19, 24, "Start"),
      Token(Flowchart.SQUARE_END, 24, 25, "]"),
      Token(WHITE_SPACE, 25, 26, " "),
      Token(Flowchart.LINK, 26, 29, "-->"),
      Token(WHITE_SPACE, 29, 30, " "),
      Token(Flowchart.NODE_ID, 30, 33, "id2"),
      Token(Flowchart.SQUARE_START, 33, 34, "["),
      Token(DOUBLE_QUOTE, 34, 35, "\""),
      Token(Flowchart.NODE_TEXT, 35, 39, "Stop"),
      Token(DOUBLE_QUOTE, 39, 40, "\""),
      Token(Flowchart.SQUARE_END, 40, 41, "]")
    )
    doTest(content, expected)
  }

  fun `test flowchart with troubled name`() {
    val content = """
    flowchart TD
      id1["This is the (text) in the box"]
    """.trimIndent()
    val expected = listOf(
      Token(Flowchart.FLOWCHART, 0, 9, "flowchart"),
      Token(WHITE_SPACE, 9, 10, " "),
      Token(Flowchart.DIR, 10, 12, "TD"),
      Token(EOL, 12, 13, "\n"),
      Token(WHITE_SPACE, 13, 15, "  "),
      Token(Flowchart.NODE_ID, 15, 18, "id1"),
      Token(Flowchart.SQUARE_START, 18, 19, "["),
      Token(DOUBLE_QUOTE, 19, 20, "\""),
      Token(Flowchart.NODE_TEXT, 20, 49, "This is the (text) in the box"),
      Token(DOUBLE_QUOTE, 49, 50, "\""),
      Token(Flowchart.SQUARE_END, 50, 51, "]")
    )
    doTest(content, expected)
  }

  fun `test flowchart with link text`() {
    val content = """
    flowchart TD
      A-- This is the text! ---B
    """.trimIndent()
    val expected = listOf(
      Token(Flowchart.FLOWCHART, 0, 9, "flowchart"),
      Token(WHITE_SPACE, 9, 10, " "),
      Token(Flowchart.DIR, 10, 12, "TD"),
      Token(EOL, 12, 13, "\n"),
      Token(WHITE_SPACE, 13, 15, "  "),
      Token(Flowchart.NODE_ID, 15, 16, "A"),
      Token(Flowchart.START_LINK, 16, 18, "--"),
      Token(Flowchart.LINK_TEXT, 18, 37, " This is the text! "),
      Token(Flowchart.LINK, 37, 40, "---"),
      Token(Flowchart.NODE_ID, 40, 41, "B")
    )
    doTest(content, expected)
  }

  fun `test flowchart with arrow link and text`() {
    val content = """
    flowchart TD
      A-- This is the text! -->B
    """.trimIndent()
    val expected = listOf(
      Token(Flowchart.FLOWCHART, 0, 9, "flowchart"),
      Token(WHITE_SPACE, 9, 10, " "),
      Token(Flowchart.DIR, 10, 12, "TD"),
      Token(EOL, 12, 13, "\n"),
      Token(WHITE_SPACE, 13, 15, "  "),
      Token(Flowchart.NODE_ID, 15, 16, "A"),
      Token(Flowchart.START_LINK, 16, 18, "--"),
      Token(Flowchart.LINK_TEXT, 18, 37, " This is the text! "),
      Token(Flowchart.LINK, 37, 40, "-->"),
      Token(Flowchart.NODE_ID, 40, 41, "B")
    )
    doTest(content, expected)
  }

  fun `test flowchart with link text in the end`() {
    val content = """
    flowchart LR
      A---|This is the text|B
    """.trimIndent()
    val expected = listOf(
      Token(Flowchart.FLOWCHART, 0, 9, "flowchart"),
      Token(WHITE_SPACE, 9, 10, " "),
      Token(Flowchart.DIR, 10, 12, "LR"),
      Token(EOL, 12, 13, "\n"),
      Token(WHITE_SPACE, 13, 15, "  "),
      Token(Flowchart.NODE_ID, 15, 16, "A"),
      Token(Flowchart.LINK, 16, 19, "---"),
      Token(Flowchart.SEP, 19, 20, "|"),
      Token(Flowchart.LINK_TEXT, 20, 36, "This is the text"),
      Token(Flowchart.SEP, 36, 37, "|"),
      Token(Flowchart.NODE_ID, 37, 38, "B")
    )
    doTest(content, expected)
  }
  fun `test flowchart with link with arrow head and text`() {
    val content = """
    flowchart LR
      A-->|This is the text|B
    """.trimIndent()
    val expected = listOf(
      Token(Flowchart.FLOWCHART, 0, 9, "flowchart"),
      Token(WHITE_SPACE, 9, 10, " "),
      Token(Flowchart.DIR, 10, 12, "LR"),
      Token(EOL, 12, 13, "\n"),
      Token(WHITE_SPACE, 13, 15, "  "),
      Token(Flowchart.NODE_ID, 15, 16, "A"),
      Token(Flowchart.LINK, 16, 19, "-->"),
      Token(Flowchart.SEP, 19, 20, "|"),
      Token(Flowchart.LINK_TEXT, 20, 36, "This is the text"),
      Token(Flowchart.SEP, 36, 37, "|"),
      Token(Flowchart.NODE_ID, 37, 38, "B")
    )
    doTest(content, expected)
  }

  fun `test flowchart with dotted link with text`() {
    val content = """
    flowchart TD
      A-. This is the text! .->B
    """.trimIndent()
    val expected = listOf(
      Token(Flowchart.FLOWCHART, 0, 9, "flowchart"),
      Token(WHITE_SPACE, 9, 10, " "),
      Token(Flowchart.DIR, 10, 12, "TD"),
      Token(EOL, 12, 13, "\n"),
      Token(WHITE_SPACE, 13, 15, "  "),
      Token(Flowchart.NODE_ID, 15, 16, "A"),
      Token(Flowchart.START_LINK, 16, 18, "-."),
      Token(Flowchart.LINK_TEXT, 18, 37, " This is the text! "),
      Token(Flowchart.LINK, 37, 40, ".->"),
      Token(Flowchart.NODE_ID, 40, 41, "B")
    )
    doTest(content, expected)
  }

  fun `test flowchart with chaining of links`() {
    val content = """
    flowchart TD
      A -- te-xt1 --> B -- text2 --> C
    """.trimIndent()
    val expected = listOf(
      Token(Flowchart.FLOWCHART, 0, 9, "flowchart"),
      Token(WHITE_SPACE, 9, 10, " "),
      Token(Flowchart.DIR, 10, 12, "TD"),
      Token(EOL, 12, 13, "\n"),
      Token(WHITE_SPACE, 13, 15, "  "),
      Token(Flowchart.NODE_ID, 15, 16, "A"),
      Token(WHITE_SPACE, 16, 17, " "),
      Token(Flowchart.START_LINK, 17, 19, "--"),
      Token(Flowchart.LINK_TEXT, 19, 22, " te"),
      Token(Flowchart.LINK_TEXT, 22, 23, "-"),
      Token(Flowchart.LINK_TEXT, 23, 27, "xt1 "),
      Token(Flowchart.LINK, 27, 30, "-->"),
      Token(WHITE_SPACE, 30, 31, " "),
      Token(Flowchart.NODE_ID, 31, 32, "B"),
      Token(WHITE_SPACE, 32, 33, " "),
      Token(Flowchart.START_LINK, 33, 35, "--"),
      Token(Flowchart.LINK_TEXT, 35, 42, " text2 "),
      Token(Flowchart.LINK, 42, 45, "-->"),
      Token(WHITE_SPACE, 45, 46, " "),
      Token(Flowchart.NODE_ID, 46, 47, "C")
    )
    doTest(content, expected)
  }

  fun `test flowchart with another chaining of links`() {
    val content = """
    flowchart TD
      A -- te-xt1 --> B -->|text2| C
    """.trimIndent()
    val expected = listOf(
      Token(Flowchart.FLOWCHART, 0, 9, "flowchart"),
      Token(WHITE_SPACE, 9, 10, " "),
      Token(Flowchart.DIR, 10, 12, "TD"),
      Token(EOL, 12, 13, "\n"),
      Token(WHITE_SPACE, 13, 15, "  "),
      Token(Flowchart.NODE_ID, 15, 16, "A"),
      Token(WHITE_SPACE, 16, 17, " "),
      Token(Flowchart.START_LINK, 17, 19, "--"),
      Token(Flowchart.LINK_TEXT, 19, 22, " te"),
      Token(Flowchart.LINK_TEXT, 22, 23, "-"),
      Token(Flowchart.LINK_TEXT, 23, 27, "xt1 "),
      Token(Flowchart.LINK, 27, 30, "-->"),
      Token(WHITE_SPACE, 30, 31, " "),
      Token(Flowchart.NODE_ID, 31, 32, "B"),
      Token(WHITE_SPACE, 32, 33, " "),
      Token(Flowchart.LINK, 33, 36, "-->"),
      Token(Flowchart.SEP, 36, 37, "|"),
      Token(Flowchart.LINK_TEXT, 37, 42, "text2"),
      Token(Flowchart.SEP, 42, 43, "|"),
      Token(WHITE_SPACE, 43, 44, " "),
      Token(Flowchart.NODE_ID, 44, 45, "C")
    )
    doTest(content, expected)
  }

  fun `test flowchart with ampersand chaining of nodes`() {
    val content = """
    flowchart LR
      a --> b & c--> d
    """.trimIndent()
    val expected = listOf(
      Token(Flowchart.FLOWCHART, 0, 9, "flowchart"),
      Token(WHITE_SPACE, 9, 10, " "),
      Token(Flowchart.DIR, 10, 12, "LR"),
      Token(EOL, 12, 13, "\n"),
      Token(WHITE_SPACE, 13, 15, "  "),
      Token(Flowchart.NODE_ID, 15, 16, "a"),
      Token(WHITE_SPACE, 16, 17, " "),
      Token(Flowchart.LINK, 17, 20, "-->"),
      Token(WHITE_SPACE, 20, 21, " "),
      Token(Flowchart.NODE_ID, 21, 22, "b"),
      Token(Flowchart.AMPERSAND, 22, 25, " & "),
      Token(Flowchart.NODE_ID, 25, 26, "c"),
      Token(Flowchart.LINK, 26, 29, "-->"),
      Token(WHITE_SPACE, 29, 30, " "),
      Token(Flowchart.NODE_ID, 30, 31, "d")
    )
    doTest(content, expected)
  }

  fun `test flowchart with multi directional arrows`() {
    val content = """
    flowchart LR
      A o--o B
      B <--> C
      C x--x D
    """.trimIndent()
    val expected = listOf(
      Token(Flowchart.FLOWCHART, 0, 9, "flowchart"),
      Token(WHITE_SPACE, 9, 10, " "),
      Token(Flowchart.DIR, 10, 12, "LR"),
      Token(EOL, 12, 13, "\n"),
      Token(WHITE_SPACE, 13, 15, "  "),
      Token(Flowchart.NODE_ID, 15, 16, "A"),
      Token(WHITE_SPACE, 16, 17, " "),
      Token(Flowchart.LINK, 17, 21, "o--o"),
      Token(WHITE_SPACE, 21, 22, " "),
      Token(Flowchart.NODE_ID, 22, 23, "B"),
      Token(EOL, 23, 24, "\n"),
      Token(WHITE_SPACE, 24, 26, "  "),
      Token(Flowchart.NODE_ID, 26, 27, "B"),
      Token(WHITE_SPACE, 27, 28, " "),
      Token(Flowchart.LINK, 28, 32, "<-->"),
      Token(WHITE_SPACE, 32, 33, " "),
      Token(Flowchart.NODE_ID, 33, 34, "C"),
      Token(EOL, 34, 35, "\n"),
      Token(WHITE_SPACE, 35, 37, "  "),
      Token(Flowchart.NODE_ID, 37, 38, "C"),
      Token(WHITE_SPACE, 38, 39, " "),
      Token(Flowchart.LINK, 39, 43, "x--x"),
      Token(WHITE_SPACE, 43, 44, " "),
      Token(Flowchart.NODE_ID, 44, 45, "D")
    )
    doTest(content, expected)
  }

  fun `test flowchart with subgraphs`() {
    val content = """
    flowchart TB
      c1-->a2
      subgraph one
      a1-->a2
      end
      subgraph two
      b1-->b2
      end
      subgraph three
      c1-->c2
      end
      one --> two
      three --> two
      two --> c2
    """.trimIndent()
    val expected = listOf(
      Token(Flowchart.FLOWCHART, 0, 9, "flowchart"),
      Token(WHITE_SPACE, 9, 10, " "),
      Token(Flowchart.DIR, 10, 12, "TB"),
      Token(EOL, 12, 13, "\n"),
      Token(WHITE_SPACE, 13, 15, "  "),
      Token(Flowchart.NODE_ID, 15, 17, "c1"),
      Token(Flowchart.LINK, 17, 20, "-->"),
      Token(Flowchart.NODE_ID, 20, 22, "a2"),
      Token(EOL, 22, 23, "\n"),
      Token(WHITE_SPACE, 23, 25, "  "),
      Token(SUBGRAPH, 25, 33, "subgraph"),
      Token(WHITE_SPACE, 33, 34, " "),
      Token(Flowchart.NODE_ID, 34, 37, "one"),
      Token(EOL, 37, 38, "\n"),
      Token(WHITE_SPACE, 38, 40, "  "),
      Token(Flowchart.NODE_ID, 40, 42, "a1"),
      Token(Flowchart.LINK, 42, 45, "-->"),
      Token(Flowchart.NODE_ID, 45, 47, "a2"),
      Token(EOL, 47, 48, "\n"),
      Token(WHITE_SPACE, 48, 50, "  "),
      Token(END, 50, 53, "end"),
      Token(EOL, 53, 54, "\n"),
      Token(WHITE_SPACE, 54, 56, "  "),
      Token(SUBGRAPH, 56, 64, "subgraph"),
      Token(WHITE_SPACE, 64, 65, " "),
      Token(Flowchart.NODE_ID, 65, 68, "two"),
      Token(EOL, 68, 69, "\n"),
      Token(WHITE_SPACE, 69, 71, "  "),
      Token(Flowchart.NODE_ID, 71, 73, "b1"),
      Token(Flowchart.LINK, 73, 76, "-->"),
      Token(Flowchart.NODE_ID, 76, 78, "b2"),
      Token(EOL, 78, 79, "\n"),
      Token(WHITE_SPACE, 79, 81, "  "),
      Token(END, 81, 84, "end"),
      Token(EOL, 84, 85, "\n"),
      Token(WHITE_SPACE, 85, 87, "  "),
      Token(SUBGRAPH, 87, 95, "subgraph"),
      Token(WHITE_SPACE, 95, 96, " "),
      Token(Flowchart.NODE_ID, 96, 101, "three"),
      Token(EOL, 101, 102, "\n"),
      Token(WHITE_SPACE, 102, 104, "  "),
      Token(Flowchart.NODE_ID, 104, 106, "c1"),
      Token(Flowchart.LINK, 106, 109, "-->"),
      Token(Flowchart.NODE_ID, 109, 111, "c2"),
      Token(EOL, 111, 112, "\n"),
      Token(WHITE_SPACE, 112, 114, "  "),
      Token(END, 114, 117, "end"),
      Token(EOL, 117, 118, "\n"),
      Token(WHITE_SPACE, 118, 120, "  "),
      Token(Flowchart.NODE_ID, 120, 123, "one"),
      Token(WHITE_SPACE, 123, 124, " "),
      Token(Flowchart.LINK, 124, 127, "-->"),
      Token(WHITE_SPACE, 127, 128, " "),
      Token(Flowchart.NODE_ID, 128, 131, "two"),
      Token(EOL, 131, 132, "\n"),
      Token(WHITE_SPACE, 132, 134, "  "),
      Token(Flowchart.NODE_ID, 134, 139, "three"),
      Token(WHITE_SPACE, 139, 140, " "),
      Token(Flowchart.LINK, 140, 143, "-->"),
      Token(WHITE_SPACE, 143, 144, " "),
      Token(Flowchart.NODE_ID, 144, 147, "two"),
      Token(EOL, 147, 148, "\n"),
      Token(WHITE_SPACE, 148, 150, "  "),
      Token(Flowchart.NODE_ID, 150, 153, "two"),
      Token(WHITE_SPACE, 153, 154, " "),
      Token(Flowchart.LINK, 154, 157, "-->"),
      Token(WHITE_SPACE, 157, 158, " "),
      Token(Flowchart.NODE_ID, 158, 160, "c2")
    )
    doTest(content, expected)
  }

  fun `test flowchart with complex subgraphs`() {
    val content = """
    flowchart LR
      subgraph TOP
        direction TB
        subgraph B1
            direction RL
            i1 -->f1
        end
        subgraph B2
            direction BT
            i2 -->f2
        end
      end
      A --> TOP --> B
      B1 --> B2
    """.trimIndent()
    val expected = listOf(
      Token(Flowchart.FLOWCHART, 0, 9, "flowchart"),
      Token(WHITE_SPACE, 9, 10, " "),
      Token(Flowchart.DIR, 10, 12, "LR"),
      Token(EOL, 12, 13, "\n"),
      Token(WHITE_SPACE, 13, 15, "  "),
      Token(SUBGRAPH, 15, 23, "subgraph"),
      Token(WHITE_SPACE, 23, 24, " "),
      Token(Flowchart.NODE_ID, 24, 27, "TOP"),
      Token(EOL, 27, 28, "\n"),
      Token(WHITE_SPACE, 28, 32, "    "),
      Token(DIRECTION, 32, 41, "direction"),
      Token(WHITE_SPACE, 41, 42, " "),
      Token(Flowchart.DIR, 42, 44, "TB"),
      Token(EOL, 44, 45, "\n"),
      Token(WHITE_SPACE, 45, 49, "    "),
      Token(SUBGRAPH, 49, 57, "subgraph"),
      Token(WHITE_SPACE, 57, 58, " "),
      Token(Flowchart.NODE_ID, 58, 60, "B1"),
      Token(EOL, 60, 61, "\n"),
      Token(WHITE_SPACE, 61, 69, "        "),
      Token(DIRECTION, 69, 78, "direction"),
      Token(WHITE_SPACE, 78, 79, " "),
      Token(Flowchart.DIR, 79, 81, "RL"),
      Token(EOL, 81, 82, "\n"),
      Token(WHITE_SPACE, 82, 90, "        "),
      Token(Flowchart.NODE_ID, 90, 92, "i1"),
      Token(WHITE_SPACE, 92, 93, " "),
      Token(Flowchart.LINK, 93, 96, "-->"),
      Token(Flowchart.NODE_ID, 96, 98, "f1"),
      Token(EOL, 98, 99, "\n"),
      Token(WHITE_SPACE, 99, 103, "    "),
      Token(END, 103, 106, "end"),
      Token(EOL, 106, 107, "\n"),
      Token(WHITE_SPACE, 107, 111, "    "),
      Token(SUBGRAPH, 111, 119, "subgraph"),
      Token(WHITE_SPACE, 119, 120, " "),
      Token(Flowchart.NODE_ID, 120, 122, "B2"),
      Token(EOL, 122, 123, "\n"),
      Token(WHITE_SPACE, 123, 131, "        "),
      Token(DIRECTION, 131, 140, "direction"),
      Token(WHITE_SPACE, 140, 141, " "),
      Token(Flowchart.DIR, 141, 143, "BT"),
      Token(EOL, 143, 144, "\n"),
      Token(WHITE_SPACE, 144, 152, "        "),
      Token(Flowchart.NODE_ID, 152, 154, "i2"),
      Token(WHITE_SPACE, 154, 155, " "),
      Token(Flowchart.LINK, 155, 158, "-->"),
      Token(Flowchart.NODE_ID, 158, 160, "f2"),
      Token(EOL, 160, 161, "\n"),
      Token(WHITE_SPACE, 161, 165, "    "),
      Token(END, 165, 168, "end"),
      Token(EOL, 168, 169, "\n"),
      Token(WHITE_SPACE, 169, 171, "  "),
      Token(END, 171, 174, "end"),
      Token(EOL, 174, 175, "\n"),
      Token(WHITE_SPACE, 175, 177, "  "),
      Token(Flowchart.NODE_ID, 177, 178, "A"),
      Token(WHITE_SPACE, 178, 179, " "),
      Token(Flowchart.LINK, 179, 182, "-->"),
      Token(WHITE_SPACE, 182, 183, " "),
      Token(Flowchart.NODE_ID, 183, 186, "TOP"),
      Token(WHITE_SPACE, 186, 187, " "),
      Token(Flowchart.LINK, 187, 190, "-->"),
      Token(WHITE_SPACE, 190, 191, " "),
      Token(Flowchart.NODE_ID, 191, 192, "B"),
      Token(EOL, 192, 193, "\n"),
      Token(WHITE_SPACE, 193, 195, "  "),
      Token(Flowchart.NODE_ID, 195, 197, "B1"),
      Token(WHITE_SPACE, 197, 198, " "),
      Token(Flowchart.LINK, 198, 201, "-->"),
      Token(WHITE_SPACE, 201, 202, " "),
      Token(Flowchart.NODE_ID, 202, 204, "B2")
    )
    doTest(content, expected)
  }

  fun `test flowchart with semicolon sep`() {
    val content = """
    flowchart LR
      q-->a;w;e;
    """.trimIndent()
    val expected = listOf(
      Token(Flowchart.FLOWCHART, 0, 9, "flowchart"),
      Token(WHITE_SPACE, 9, 10, " "),
      Token(Flowchart.DIR, 10, 12, "LR"),
      Token(EOL, 12, 13, "\n"),
      Token(WHITE_SPACE, 13, 15, "  "),
      Token(Flowchart.NODE_ID, 15, 16, "q"),
      Token(Flowchart.LINK, 16, 19, "-->"),
      Token(Flowchart.NODE_ID, 19, 20, "a"),
      Token(SEMICOLON, 20, 21, ";"),
      Token(Flowchart.NODE_ID, 21, 22, "w"),
      Token(SEMICOLON, 22, 23, ";"),
      Token(Flowchart.NODE_ID, 23, 24, "e"),
      Token(SEMICOLON, 24, 25, ";")
    )
    doTest(content, expected)
  }

  fun `test flowchart with styles`() {
    val content = """
    flowchart LR
      id1(Start)-->id2(Stop)
      
      style id1 fill:#f9f,stroke:#333,stroke-width:4px
      style id2 fill:#bbf,stroke:#f66,stroke-width:2px,color:#fff,stroke-dasharray: 5 5
      
      A:::someclass --> B
      linkStyle 0,1 stroke:#ff3,stroke-width:4px,color:red;
      C
      classDef someclass fill:#f96;
      class B,C someclass
    """.trimIndent()
    val expected = listOf(
      Token(Flowchart.FLOWCHART, 0, 9, "flowchart"),
      Token(WHITE_SPACE, 9, 10, " "),
      Token(Flowchart.DIR, 10, 12, "LR"),
      Token(EOL, 12, 13, "\n"),
      Token(WHITE_SPACE, 13, 15, "  "),
      Token(Flowchart.NODE_ID, 15, 18, "id1"),
      Token(Flowchart.ROUND_START, 18, 19, "("),
      Token(Flowchart.NODE_TEXT, 19, 24, "Start"),
      Token(Flowchart.ROUND_END, 24, 25, ")"),
      Token(Flowchart.LINK, 25, 28, "-->"),
      Token(Flowchart.NODE_ID, 28, 31, "id2"),
      Token(Flowchart.ROUND_START, 31, 32, "("),
      Token(Flowchart.NODE_TEXT, 32, 36, "Stop"),
      Token(Flowchart.ROUND_END, 36, 37, ")"),
      Token(EOL, 37, 38, "\n"),
      Token(WHITE_SPACE, 38, 40, "  "),
      Token(EOL, 40, 41, "\n"),
      Token(WHITE_SPACE, 41, 43, "  "),
      Token(STYLE, 43, 48, "style"),
      Token(WHITE_SPACE, 48, 49, " "),
      Token(STYLE_TARGET, 49, 52, "id1"),
      Token(WHITE_SPACE, 52, 53, " "),
      Token(STYLE_OPT, 53, 57, "fill"),
      Token(COLON, 57, 58, ":"),
      Token(STYLE_VAL, 58, 62, "#f9f"),
      Token(COMMA, 62, 63, ","),
      Token(STYLE_OPT, 63, 69, "stroke"),
      Token(COLON, 69, 70, ":"),
      Token(STYLE_VAL, 70, 74, "#333"),
      Token(COMMA, 74, 75, ","),
      Token(STYLE_OPT, 75, 87, "stroke-width"),
      Token(COLON, 87, 88, ":"),
      Token(STYLE_VAL, 88, 91, "4px"),
      Token(EOL, 91, 92, "\n"),
      Token(WHITE_SPACE, 92, 94, "  "),
      Token(STYLE, 94, 99, "style"),
      Token(WHITE_SPACE, 99, 100, " "),
      Token(STYLE_TARGET, 100, 103, "id2"),
      Token(WHITE_SPACE, 103, 104, " "),
      Token(STYLE_OPT, 104, 108, "fill"),
      Token(COLON, 108, 109, ":"),
      Token(STYLE_VAL, 109, 113, "#bbf"),
      Token(COMMA, 113, 114, ","),
      Token(STYLE_OPT, 114, 120, "stroke"),
      Token(COLON, 120, 121, ":"),
      Token(STYLE_VAL, 121, 125, "#f66"),
      Token(COMMA, 125, 126, ","),
      Token(STYLE_OPT, 126, 138, "stroke-width"),
      Token(COLON, 138, 139, ":"),
      Token(STYLE_VAL, 139, 142, "2px"),
      Token(COMMA, 142, 143, ","),
      Token(STYLE_OPT, 143, 148, "color"),
      Token(COLON, 148, 149, ":"),
      Token(STYLE_VAL, 149, 153, "#fff"),
      Token(COMMA, 153, 154, ","),
      Token(STYLE_OPT, 154, 170, "stroke-dasharray"),
      Token(COLON, 170, 171, ":"),
      Token(WHITE_SPACE, 171, 172, " "),
      Token(STYLE_VAL, 172, 175, "5 5"),
      Token(EOL, 175, 176, "\n"),
      Token(WHITE_SPACE, 176, 178, "  "),
      Token(EOL, 178, 179, "\n"),
      Token(WHITE_SPACE, 179, 181, "  "),
      Token(Flowchart.NODE_ID, 181, 182, "A"),
      Token(STYLE_SEPARATOR, 182, 185, ":::"),
      Token(STYLE_TARGET, 185, 194, "someclass"),
      Token(WHITE_SPACE, 194, 195, " "),
      Token(Flowchart.LINK, 195, 198, "-->"),
      Token(WHITE_SPACE, 198, 199, " "),
      Token(Flowchart.NODE_ID, 199, 200, "B"),
      Token(EOL, 200, 201, "\n"),
      Token(WHITE_SPACE, 201, 203, "  "),
      Token(LINK_STYLE, 203, 212, "linkStyle"),
      Token(WHITE_SPACE, 212, 213, " "),
      Token(STYLE_TARGET, 213, 214, "0"),
      Token(COMMA, 214, 215, ","),
      Token(STYLE_TARGET, 215, 216, "1"),
      Token(WHITE_SPACE, 216, 217, " "),
      Token(STYLE_OPT, 217, 223, "stroke"),
      Token(COLON, 223, 224, ":"),
      Token(STYLE_VAL, 224, 228, "#ff3"),
      Token(COMMA, 228, 229, ","),
      Token(STYLE_OPT, 229, 241, "stroke-width"),
      Token(COLON, 241, 242, ":"),
      Token(STYLE_VAL, 242, 245, "4px"),
      Token(COMMA, 245, 246, ","),
      Token(STYLE_OPT, 246, 251, "color"),
      Token(COLON, 251, 252, ":"),
      Token(STYLE_VAL, 252, 255, "red"),
      Token(SEMICOLON, 255, 256, ";"),
      Token(EOL, 256, 257, "\n"),
      Token(WHITE_SPACE, 257, 259, "  "),
      Token(Flowchart.NODE_ID, 259, 260, "C"),
      Token(EOL, 260, 261, "\n"),
      Token(WHITE_SPACE, 261, 263, "  "),
      Token(CLASS_DEF, 263, 271, "classDef"),
      Token(WHITE_SPACE, 271, 272, " "),
      Token(STYLE_TARGET, 272, 281, "someclass"),
      Token(WHITE_SPACE, 281, 282, " "),
      Token(STYLE_OPT, 282, 286, "fill"),
      Token(COLON, 286, 287, ":"),
      Token(STYLE_VAL, 287, 291, "#f96"),
      Token(SEMICOLON, 291, 292, ";"),
      Token(EOL, 292, 293, "\n"),
      Token(WHITE_SPACE, 293, 295, "  "),
      Token(CLASS, 295, 300, "class"),
      Token(WHITE_SPACE, 300, 301, " "),
      Token(Flowchart.NODE_ID, 301, 302, "B"),
      Token(COMMA, 302, 303, ","),
      Token(Flowchart.NODE_ID, 303, 304, "C"),
      Token(WHITE_SPACE, 304, 305, " "),
      Token(STYLE_TARGET, 305, 314, "someclass")
    )
    doTest(content, expected)
  }

  fun `test flowchart with comments`() {
    val content = """
    flowchart TD %% This is comment
      Start --> Stop %% This is comment
      %% This is comment
    """.trimIndent()
    val expected = listOf(
      Token(Flowchart.FLOWCHART, 0, 9, "flowchart"),
      Token(WHITE_SPACE, 9, 10, " "),
      Token(Flowchart.DIR, 10, 12, "TD"),
      Token(WHITE_SPACE, 12, 13, " "),
      Token(LINE_COMMENT, 13, 15, "%%"),
      Token(COMMENT_TEXT, 15, 31, " This is comment"),
      Token(EOL, 31, 32, "\n"),
      Token(WHITE_SPACE, 32, 34, "  "),
      Token(Flowchart.NODE_ID, 34, 39, "Start"),
      Token(WHITE_SPACE, 39, 40, " "),
      Token(Flowchart.LINK, 40, 43, "-->"),
      Token(WHITE_SPACE, 43, 44, " "),
      Token(Flowchart.NODE_ID, 44, 48, "Stop"),
      Token(WHITE_SPACE, 48, 49, " "),
      Token(LINE_COMMENT, 49, 51, "%%"),
      Token(COMMENT_TEXT, 51, 67, " This is comment"),
      Token(EOL, 67, 68, "\n"),
      Token(WHITE_SPACE, 68, 70, "  "),
      Token(LINE_COMMENT, 70, 72, "%%"),
      Token(COMMENT_TEXT, 72, 88, " This is comment")
    )
    doTest(content, expected)
  }
}
