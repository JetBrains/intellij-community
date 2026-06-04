package com.intellij.mermaid.lang.parser

class MindmapTest : MermaidParserTestCase("mindmap") {

  fun `test simple`() = doTest(true)

  fun `test node shapes`() = doTest(true)

  fun `test double quoted node description`() = doTest(true)

  fun `test icons`() = doTest(true)

  fun `test classes`() = doTest(true)

  fun `test id with colon`() = doTest(true)

  fun `test comments`() = doTest(true)
}
