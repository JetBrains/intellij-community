package com.intellij.mermaid.lang.parser

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class ClassDiagramTest : MermaidParserTestCase("class") {
  @Test
  fun `test namespace with label and dotted name`() = doTest(true)

  @Test
  fun `test nested namespaces`() = doTest(true)

  @Test
  fun `test class def statement`() = doTest(true)

  @Test
  fun `test class with trailing annotation`() = doTest(true)

  @Test
  fun `test simple class definition`() = doTest(true)

  @Test
  fun `test class definition in brackets`() = doTest(true)

  @Test
  fun `test class with generics`() = doTest(true)

  @Test
  fun `test identifiers at end of member`() = doTest(true)

  @Test
  fun `test class relationships RL`() = doTest(true)

  @Test
  fun `test class relationships LR`() = doTest(true)

  @Test
  fun `test class two way relationship`() = doTest(true)

  @Test
  fun `test class relationship with label`() = doTest(true)

  @Test
  fun `test class relationship with cardinality`() = doTest(true)

  @Test
  fun `test class with annotation`() = doTest(true)

  @Test
  fun `test class with annotation in struct`() = doTest(true)

  @Test
  fun `test class with direction`() = doTest(true)

  @Test
  fun `test class with style`() = doTest(true)

  @Test
  fun `test click statements`() = doTest(true)

  @Test
  fun `test method with several arguments`() = doTest(true)

  @Test
  fun `test class attributes with square parenthesis`() = doTest(true)

  @Test
  fun `test complex attribute`() = doTest(true)

  @Test
  fun `test notes`() = doTest(true)

  @Test
  fun `test blocks are parsed without first newline`() = doTest(true)

  @Test
  fun `test class labels`() = doTest(true)

  @Test
  fun `test backticks`() = doTest(true)

  @Test
  fun `test hyphens in names`() = doTest(true)

  @Test
  fun `test namespace`() = doTest(true)

  @Test
  fun `test namespace after class`() = doTest(true)
}
