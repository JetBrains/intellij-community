package com.intellij.mermaid.lang.parser

class JourneyTest : MermaidParserTestCase("journey") {
  fun `test simple journey`() = doTest(true)

  fun `test journey with ignored tokens`() = doTest(true)

  fun `test journey with ignored task data`() = doTest(true)
}
