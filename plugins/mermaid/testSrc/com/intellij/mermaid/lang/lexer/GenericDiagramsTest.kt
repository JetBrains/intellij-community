package com.intellij.mermaid.lang.lexer

import com.intellij.testFramework.junit5.fixture.TestFixtures
import org.junit.jupiter.api.Test

/**
 * The families that the parser accepts with the lenient generic body. Each family has its own lexer
 * state, so these tests check the keywords and the special tokens of each one.
 */
@TestFixtures
class GenericDiagramsTest {
  private val lexer by mermaidLexerFixture("generic")

  @Test
  fun `test info`() {
    val content = """
    info showInfo
    """.trimIndent()
    lexer.doTest(content)
  }

  @Test
  fun `test packet`() {
    val content = """
    packet
      title UDP Packet
      0-15: "Source Port"
      +16: "Destination Port"
    """.trimIndent()
    lexer.doTest(content)
  }

  @Test
  fun `test architecture`() {
    val content = """
    architecture-beta
      group api(cloud)[API]
      service db(database)[Database] in api
      service server(logos:aws-ec2)[Server] in api
      junction center
      db:R --> L:server
      server{group}:B -[link]- T:center
      align row db server
    """.trimIndent()
    lexer.doTest(content)
  }

  @Test
  fun `test kanban`() {
    val content = """
    kanban
      todo[Todo]
        id1[Create Documentation]@{ ticket: MC-2037, priority: 'Very High' }
      done((Done))
    """.trimIndent()
    lexer.doTest(content)
  }

  @Test
  fun `test radar`() {
    val content = """
    radar-beta
      axis m["Math"], s["Science"]
      curve a["Alice"]{85, 90}
      showLegend true
      max 100
      graticule polygon
    """.trimIndent()
    lexer.doTest(content)
  }

  @Test
  fun `test treemap`() {
    val content = """
    treemap-beta
    "Section 1"
      "Leaf 1.1": 12
      "Leaf 1.2": 1,000:::important
    classDef important fill:#f96,stroke:#333;
    """.trimIndent()
    lexer.doTest(content)
  }

  @Test
  fun `test tree view`() {
    val content = """
    treeView-beta
      title Project
      src
        index.js icon(mdi:language-javascript)
        "my file.txt" :::highlight
        README.md ## the entry point
    """.trimIndent()
    lexer.doTest(content)
  }

  @Test
  fun `test venn`() {
    val content = """
    venn-beta
      title Skills
      set A["Frontend"]:20
      union A,B[Both]
        text Shared
      style A fill:#ff0000
    """.trimIndent()
    lexer.doTest(content)
  }

  @Test
  fun `test wardley`() {
    val content = """
    wardley-beta
      size [1100, 800]
      evolution Genesis -> Custom -> Product -> Commodity
      anchor Business [0.95, 0.63]
      component Cup of Tea [0.79, 0.61] label [19, -4] (buy)
      Business -> Cup of Tea ;customer need
      evolve Cup of Tea 0.8
    """.trimIndent()
    lexer.doTest(content)
  }

  @Test
  fun `test ishikawa`() {
    val content = """
    ishikawa-beta
    Blurry Photo
    %% a comment
      Process
        Out of focus
    """.trimIndent()
    lexer.doTest(content)
  }

  @Test
  fun `test event modeling`() {
    val content = """
    eventmodeling
    tf 01 ui ProductList
    tf 02 cmd AddItem ->> 01 [[CartData]] `json` {"id": 1}
    // a comment
    data CartData {
      "items": []
    }
    """.trimIndent()
    lexer.doTest(content)
  }

  @Test
  fun `test cynefin`() {
    val content = """
    cynefin-beta
      title Decisions
      complex
        "Probe first"
      chaotic --> complex : "Stabilise"
    """.trimIndent()
    lexer.doTest(content)
  }

  @Test
  fun `test railroad`() {
    val content = """
    railroad-beta
      /* a comment */
      expr = sequence(term, zeroOrMore(choice(terminal("+"), terminal("-")), term));
    """.trimIndent()
    lexer.doTest(content)
  }

  @Test
  fun `test railroad ebnf`() {
    val content = """
    railroad-ebnf-beta
      (* a comment *)
      expr ::= term , { ( "+" | "-" ) , term } | ? any char ? ;
    """.trimIndent()
    lexer.doTest(content)
  }

  @Test
  fun `test railroad abnf`() {
    val content = """
    railroad-abnf-beta
      ; a comment
      digit = %x30-39 / 1*2"x" ;
    """.trimIndent()
    lexer.doTest(content)
  }

  @Test
  fun `test railroad peg`() {
    val content = """
    railroad-peg-beta
      # a comment
      expr <- term (('+' / '-') term)* !. ;
    """.trimIndent()
    lexer.doTest(content)
  }

  @Test
  fun `test usecase`() {
    val content = """
    usecase-beta
    direction LR
    actor Customer("Customer")@{ type: hollow }
    systemBoundary "Order system"
      Checkout("Place order")
      Report[Generate report]
    end
    Customer --> Checkout
    Checkout ..> Report : <<include>>
    """.trimIndent()
    lexer.doTest(content)
  }

  @Test
  fun `test agentflow reuses flowchart`() {
    val content = """
    agentflow-beta TB
      connector github["GitHub API"]
      global
        corpus["Shared corpus"]@{ shape: refdoc }
      end
      flow reviewer["Review Agent"]
        changes["Gather changes"]@{ shape: input }
        changes --> corpus
      end
    """.trimIndent()
    lexer.doTest(content)
  }

  @Test
  fun `test agentflow keywords are node ids in a flowchart`() {
    val content = """
    flowchart TB
      flow --> global
      connector
    """.trimIndent()
    lexer.doTest(content)
  }
}
