package com.github.firsttimeinforever.mermaid.lang.parser

class FlowchartTest : MermaidParserTestCase("flowchart") {
  fun `test simple flowchart`() = doTest(true)

  fun `test flowchart with subgraphs`() = doTest(true)

  fun `test flowchart with styles`() = doTest(true)

  fun `test click statements`() = doTest(true)
}
