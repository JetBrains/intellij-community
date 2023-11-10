package com.intellij.mermaid.lang.parser

class XYChartTest : MermaidParserTestCase("xychart") {
  fun `test simple xychart`() = doTest(true)
}
