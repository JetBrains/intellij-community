package com.intellij.mermaid.lang.parser

class FlowchartTest : MermaidParserTestCase("flowchart") {
  fun `test simple flowchart`() = doTest(true)

  fun `test flowchart with subgraphs`() = doTest(true)

  fun `test flowchart with styles`() = doTest(true)

  fun `test click statements`() = doTest(true)

  fun `test frontmatter`() = doTest(true)

  fun `test multiple vertices with style`() = doTest(true)

  fun `test node called default`() = doTest(true)

  fun `test style for node called default`() = doTest(true)
}
