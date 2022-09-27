package com.github.firsttimeinforever.mermaid.lang.lexer

import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ALIAS
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ARROW
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.CALL
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.CLASS
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.CLICK
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.CLICK_DATA
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.CLOSE_ROUND
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.CLOSE_SQUARE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COLON
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COMMA
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.DIR
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.DIRECTION
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.DOUBLE_QUOTE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.END
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.EOL
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Flowchart
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Flowchart.CLASS_DEF
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Flowchart.LINK_STYLE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Flowchart.STYLE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Flowchart.STYLE_OPT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Flowchart.STYLE_TARGET
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Flowchart.STYLE_VAL
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Flowchart.SUBGRAPH
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.HREF
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ID
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.LINE_COMMENT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.OPEN_ROUND
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.OPEN_SQUARE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.SEMICOLON
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.STRING_VALUE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.STYLE_SEPARATOR
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.WHITE_SPACE

class FlowchartTest : MermaidLexerTestCase() {
  override val diagramName: String
    get() = "flowchart"

  fun `test simple flowchart`() {
    val content = """
    flowchart TD
      Start --> Stop
    """.trimIndent()
    doTest(content)
  }

  fun `test flowchart with named nodes`() {
    val content = """
    flowchart TD
      id1[Start] --> id2["Stop"]
    """.trimIndent()
    doTest(content)
  }

  fun `test flowchart with troubled name`() {
    val content = """
    flowchart TD
      id1["This is the (text) in the box"]
    """.trimIndent()
    doTest(content)
  }

  fun `test flowchart with link text`() {
    val content = """
    flowchart TD
      A-- This is the text! ---B
    """.trimIndent()
    doTest(content)
  }

  fun `test flowchart with arrow link and text`() {
    val content = """
    flowchart TD
      A-- This is the text! -->B
    """.trimIndent()
    doTest(content)
  }

  fun `test flowchart with link text in the end`() {
    val content = """
    flowchart LR
      A---|This is the text|B
    """.trimIndent()
    doTest(content)
  }

  fun `test flowchart with link with arrow head and text`() {
    val content = """
    flowchart LR
      A-->|This is the text|B
    """.trimIndent()
    doTest(content)
  }

  fun `test flowchart with dotted link with text`() {
    val content = """
    flowchart TD
      A-. This is the text! .->B
    """.trimIndent()
    doTest(content)
  }

  fun `test flowchart with chaining of links`() {
    val content = """
    flowchart TD
      A -- te-xt1 --> B -- text2 --> C
    """.trimIndent()
    doTest(content)
  }

  fun `test flowchart with another chaining of links`() {
    val content = """
    flowchart TD
      A -- te-xt1 --> B -->|text2| C
    """.trimIndent()
    doTest(content)
  }

  fun `test flowchart with ampersand chaining of nodes`() {
    val content = """
    flowchart LR
      a --> b & c--> d
    """.trimIndent()
    doTest(content)
  }

  fun `test flowchart with multi directional arrows`() {
    val content = """
    flowchart LR
      A o--o B
      B <--> C
      C x--x D
    """.trimIndent()
    doTest(content)
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
    doTest(content)
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
    doTest(content)
  }

  fun `test flowchart with semicolon sep`() {
    val content = """
    flowchart LR
      q-->a;w;e;
    """.trimIndent()
    doTest(content)
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
    doTest(content)
  }

  fun `test flowchart with comments`() {
    val content = """
    flowchart TD %% This is comment
      Start --> Stop %% This is comment
      %% This is comment
    """.trimIndent()
    doTest(content)
  }

  fun `test click statements`() {
    val content = """
    flowchart LR
      A-->B
      click A callback "Tooltip for a callback"
      click A clbk "Tooltip for a callback"
      click clbk "Tooltip for a callback"
      click A callback
      click callback() "Tooltip for a callback"
      click B "https://www.github.com" "This is a tooltip for a link"
      click "https://www.github.com" "This is a tooltip for a link"
      click B "https://www.github.com"
      click A call callback() "Tooltip for a callback"
      click A call callback()
      click B href "https://www.github.com" "This is a tooltip for a link"
      click B href "https://www.github.com"
      click href "https://www.github.com" "This is a tooltip for a link"
    """.trimIndent()
    doTest(content)
  }
}
