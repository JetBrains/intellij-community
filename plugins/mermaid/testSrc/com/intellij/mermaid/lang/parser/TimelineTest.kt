package com.intellij.mermaid.lang.parser

class TimelineTest : MermaidParserTestCase("timeline") {
  fun `test simple`() = doTest(true)

  fun `test with ignored tokens`() = doTest(true)

  fun `test with ignored task data`() = doTest(true)
}
