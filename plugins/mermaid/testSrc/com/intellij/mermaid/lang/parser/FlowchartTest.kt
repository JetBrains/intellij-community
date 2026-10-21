package com.intellij.mermaid.lang.parser

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class FlowchartTest : MermaidParserTestCase("flowchart") {
  @Test
  fun `test simple flowchart`() = doTest(true)

  @Test
  fun `test node with shape metadata`() = doTest(true)

  @Test
  fun `test edge with id`() = doTest(true)

  @Test
  fun `test node metadata spanning lines`() = doTest(true)

  @Test
  fun `test edge metadata statement`() = doTest(true)

  @Test
  fun `test flowchart with subgraphs`() = doTest(true)

  @Test
  fun `test flowchart with styles`() = doTest(true)

  @Test
  fun `test click statements`() = doTest(true)

  @Test
  fun `test frontmatter`() = doTest(true)

  @Test
  fun `test multiple vertices with style`() = doTest(true)

  @Test
  fun `test node called default`() = doTest(true)

  @Test
  fun `test style for node called default`() = doTest(true)
}
