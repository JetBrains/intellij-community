package com.intellij.mermaid.lang.parser

class QuadrantTest : MermaidParserTestCase("quadrant") {
  fun `test simple quadrant`() = doTest(true)
}
