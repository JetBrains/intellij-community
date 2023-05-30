package com.intellij.mermaid.lang.parser

class ClassDiagramTest : MermaidParserTestCase("class") {
  fun `test simple class definition`() = doTest(true)

  fun `test class definition in brackets`() = doTest(true)

  fun `test class with generics`() = doTest(true)

  fun `test identifiers at end of member`() = doTest(true)

  fun `test class relationships RL`() = doTest(true)

  fun `test class relationships LR`() = doTest(true)

  fun `test class two way relationship`() = doTest(true)

  fun `test class relationship with label`() = doTest(true)

  fun `test class relationship with cardinality`() = doTest(true)

  fun `test class with annotation`() = doTest(true)

  fun `test class with annotation in struct`() = doTest(true)

  fun `test class with direction`() = doTest(true)

  fun `test class with style`() = doTest(true)

  fun `test click statements`() = doTest(true)

  fun `test method with several arguments`() = doTest(true)

  fun `test class attributes with square parenthesis`() = doTest(true)

  fun `test complex attribute`() = doTest(true)

  fun `test notes`() = doTest(true)

  fun `test blocks are parsed without first newline`() = doTest(true)

  fun `test class labels`() = doTest(true)

  fun `test backticks`() = doTest(true)

  fun `test hyphens in names`() = doTest(true)

  fun `test namespace`() = doTest(true)

  fun `test namespace after class`() = doTest(true)
}
