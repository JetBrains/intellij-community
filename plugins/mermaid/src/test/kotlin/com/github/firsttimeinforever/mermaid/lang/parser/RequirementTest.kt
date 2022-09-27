package com.github.firsttimeinforever.mermaid.lang.parser

class RequirementTest : MermaidParserTestCase("requirement") {
  fun `test simple class requirement diagram`() = doTest(true)

  fun `test full complex requirement diagram`() = doTest(true)
}
