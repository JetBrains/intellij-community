package com.intellij.mermaid.lang.parser

class BlockTest : MermaidParserTestCase("block") {
  fun `test complex block diagram`() = doTest(true)
}
