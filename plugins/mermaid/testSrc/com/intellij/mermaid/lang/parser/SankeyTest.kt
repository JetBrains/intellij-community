package com.intellij.mermaid.lang.parser

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class SankeyTest : MermaidParserTestCase("sankey") {
  @Test
  fun `test sankey`() = doTest(true)
}
