package com.intellij.mermaid.lang.parser

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class XYChartTest : MermaidParserTestCase("xychart") {
  @Test
  fun `test simple xychart`() = doTest(true)
}
