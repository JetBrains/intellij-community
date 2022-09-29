package com.intellij.mermaid.lang.parser

class C4Test : MermaidParserTestCase("c4") {
  fun `test c4 context`() = doTest(true)

  fun `test c4 container`() = doTest(true)

  fun `test c4 component`() = doTest(true)

  fun `test c4 dynamic`() = doTest(true)

  fun `test c4 deployment`() = doTest(true)
}
