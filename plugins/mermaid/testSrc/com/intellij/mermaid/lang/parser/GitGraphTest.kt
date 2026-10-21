package com.intellij.mermaid.lang.parser

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class GitGraphTest : MermaidParserTestCase("gitGraph") {
  @Test
  fun `test simple git graph`() = doTest(true)

  @Test
  fun `test git graph with custom commit id`() = doTest(true)

  @Test
  fun `test commit type`() = doTest(true)

  @Test
  fun `test commit tags`() = doTest(true)

  @Test
  fun `test cherry pick`() = doTest(true)

  @Test
  fun `test order`() = doTest(true)

  @Test
  fun `test merge`() = doTest(true)

  @Test
  fun `test quoted branch names`() = doTest(true)

  @Test
  fun `test colon`() = doTest(true)

  @Test
  fun `test dir`() = doTest(true)
}
