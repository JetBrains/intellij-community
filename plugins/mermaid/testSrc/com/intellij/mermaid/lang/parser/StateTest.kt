package com.intellij.mermaid.lang.parser

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class StateTest : MermaidParserTestCase("state") {
  @Test
  fun `test different state definition`() = doTest(true)

  @Test
  fun `test transitions`() = doTest(true)

  @Test
  fun `test special start and end states`() = doTest(true)

  @Test
  fun `test composite states`() = doTest(true)

  @Test
  fun `test state annotation`() = doTest(true)

  @Test
  fun `test notes`() = doTest(true)

  @Test
  fun `test diagram with concurrency`() = doTest(true)

  @Test
  fun `test direction`() = doTest(true)

  @Test
  fun `test comments`() = doTest(true)

  @Test
  fun `test class def`() = doTest(true)
}
