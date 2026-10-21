package com.intellij.mermaid.lang.parser

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class GanttTest : MermaidParserTestCase("gantt") {
  @Test
  fun `test simple gantt`() = doTest(true)

  @Test
  fun `test full complex`() = doTest(true)

  @Test
  fun `test click statements`() = doTest(true)

  @Test
  fun `test today marker`() = doTest(true)

  @Test
  fun `test tick interval`() = doTest(true)
}
