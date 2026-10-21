package com.intellij.mermaid.lang.parser

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class RequirementTest : MermaidParserTestCase("requirement") {
  @Test
  fun `test simple class requirement diagram`() = doTest(true)

  @Test
  fun `test full complex requirement diagram`() = doTest(true)
}
