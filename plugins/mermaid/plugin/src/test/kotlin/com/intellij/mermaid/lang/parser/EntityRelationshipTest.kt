package com.intellij.mermaid.lang.parser

class EntityRelationshipTest : MermaidParserTestCase("entityRelationship") {
  fun `test simple entity relationship`() = doTest(true)

  fun `test entity with attribute keys and comments`() = doTest(true)

  fun `test entity relationship with double quoted label`() = doTest(true)

  fun `test entity names with double quotes`() = doTest(true)

  fun `test cardinality aliases`() = doTest(true)

  fun `test attr keys`() = doTest(true)

  fun `test parent-child relationship`() = doTest(true)

  fun `test entity alias`() = doTest(true)
}
