package com.intellij.mermaid.lang.parser

class ZenUMLTest : MermaidParserTestCase("zenUML") {
  fun `test zenUML`() = doTest(true)
}
