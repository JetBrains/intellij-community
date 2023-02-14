package com.intellij.mermaid.lang.parser

class PieTest : MermaidParserTestCase("pie") {
  fun `test simple pie`() = doTest(true)

  fun `test pie with simple header`() = doTest(true)
}
