package com.intellij.mermaid.lang.parser

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class BlockTest : MermaidParserTestCase("block") {
  @Test
  fun `test complex block diagram`() = doTest(true)
}
