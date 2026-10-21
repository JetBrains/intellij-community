package com.intellij.mermaid.lang.parser

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class EntityRelationshipTest : MermaidParserTestCase("entityRelationship") {
  @Test
  fun `test simple entity relationship`() = doTest(true)

  @Test
  fun `test entity with attribute keys and comments`() = doTest(true)

  @Test
  fun `test entity relationship with double quoted label`() = doTest(true)

  @Test
  fun `test entity names with double quotes`() = doTest(true)

  @Test
  fun `test cardinality aliases`() = doTest(true)

  @Test
  fun `test attr keys`() = doTest(true)

  @Test
  fun `test parent-child relationship`() = doTest(true)

  @Test
  fun `test entity alias`() = doTest(true)

  @Test
  fun `test subgraphs`() = doTest(true)
}
