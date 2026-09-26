package com.intellij.mermaid.lang.parser

class StateTest : MermaidParserTestCase("state") {
  fun `test different state definition`() = doTest(true)

  fun `test transitions`() = doTest(true)

  fun `test special start and end states`() = doTest(true)

  fun `test composite states`() = doTest(true)

  fun `test state annotation`() = doTest(true)

  fun `test notes`() = doTest(true)

  fun `test diagram with concurrency`() = doTest(true)

  fun `test direction`() = doTest(true)

  fun `test comments`() = doTest(true)

  fun `test class def`() = doTest(true)
}
