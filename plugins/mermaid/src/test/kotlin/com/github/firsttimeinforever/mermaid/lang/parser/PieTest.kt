package com.github.firsttimeinforever.mermaid.lang.parser

class PieTest : MermaidParserTestCase("pie") {
  fun `test simple pie`() = doTest(true)
}
