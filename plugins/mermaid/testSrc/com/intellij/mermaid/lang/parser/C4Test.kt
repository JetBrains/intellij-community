package com.intellij.mermaid.lang.parser

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class C4Test : MermaidParserTestCase("c4") {
  @Test
  fun `test c4 context`() = doTest(true)

  @Test
  fun `test c4 container`() = doTest(true)

  @Test
  fun `test c4 component`() = doTest(true)

  @Test
  fun `test c4 dynamic`() = doTest(true)

  @Test
  fun `test c4 deployment`() = doTest(true)
}
