package com.intellij.mermaid.lang.parser

class GitGraphTest : MermaidParserTestCase("gitGraph") {
  fun `test simple git graph`() = doTest(true)

  fun `test git graph with custom commit id`() = doTest(true)

  fun `test commit type`() = doTest(true)

  fun `test commit tags`() = doTest(true)

  fun `test cherry pick`() = doTest(true)

  fun `test order`() = doTest(true)
}
