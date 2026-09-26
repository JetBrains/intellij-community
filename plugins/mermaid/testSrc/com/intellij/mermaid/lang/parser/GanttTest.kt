package com.intellij.mermaid.lang.parser

class GanttTest : MermaidParserTestCase("gantt") {
  fun `test simple gantt`() = doTest(true)

  fun `test full complex`() = doTest(true)

  fun `test click statements`() = doTest(true)

  fun `test today marker`() = doTest(true)

  fun `test tick interval`() = doTest(true)
}
