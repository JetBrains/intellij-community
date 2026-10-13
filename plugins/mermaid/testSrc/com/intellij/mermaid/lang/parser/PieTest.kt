package com.intellij.mermaid.lang.parser

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class PieTest : MermaidParserTestCase("pie") {
  @Test
  fun `test simple pie`() = doTest(true)

  @Test
  fun `test pie with simple header`() = doTest(true)
}
