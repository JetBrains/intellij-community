package com.intellij.mermaid.lang.parser

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class SequenceTest : MermaidParserTestCase("sequence") {
  @Test
  fun `test participant metadata`() = doTest(true)

  @Test
  fun `test simple sequence`() = doTest(true)

  @Test
  fun `test sequence with short activations`() = doTest(true)

  @Test
  fun `test sequence with notes`() = doTest(true)

  @Test
  fun `test sequence with json formatted link`() = doTest(true)

  @Test
  fun `test sequence with loop`() = doTest(true)

  @Test
  fun `test autonumber`() = doTest(true)

  @Test
  fun `test critical region`() = doTest(true)

  @Test
  fun `test break`() = doTest(true)

  @Test
  fun `test box`() = doTest(true)

  @Test
  fun `test par_over`() = doTest(true)

  @Test
  fun `test directives`() = doTest(true)

  @Test
  fun `test entity codes`() = doTest(true)
}
