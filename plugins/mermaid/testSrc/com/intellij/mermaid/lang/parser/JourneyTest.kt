package com.intellij.mermaid.lang.parser

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class JourneyTest : MermaidParserTestCase("journey") {
  @Test
  fun `test simple journey`() = doTest(true)

  @Test
  fun `test journey with ignored tokens`() = doTest(true)

  @Test
  fun `test journey with ignored task data`() = doTest(true)
}
