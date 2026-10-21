package com.intellij.mermaid.lang.parser

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class ZenUMLTest : MermaidParserTestCase("zenUML") {
  @Test
  fun `test zenUML`() = doTest(true)
}
