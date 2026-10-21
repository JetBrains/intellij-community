package com.intellij.mermaid.lang.parser

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class TimelineTest : MermaidParserTestCase("timeline") {
  @Test
  fun `test simple`() = doTest(true)

  @Test
  fun `test with ignored tokens`() = doTest(true)

  @Test
  fun `test with ignored task data`() = doTest(true)
}
