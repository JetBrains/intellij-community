package com.intellij.mermaid.lang.parser

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class MindmapTest : MermaidParserTestCase("mindmap") {

  @Test
  fun `test simple`() = doTest(true)

  @Test
  fun `test node shapes`() = doTest(true)

  @Test
  fun `test double quoted node description`() = doTest(true)

  @Test
  fun `test icons`() = doTest(true)

  @Test
  fun `test classes`() = doTest(true)

  @Test
  fun `test id with colon`() = doTest(true)

  @Test
  fun `test comments`() = doTest(true)
}
