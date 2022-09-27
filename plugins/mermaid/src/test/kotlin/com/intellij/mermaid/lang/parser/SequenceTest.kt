package com.intellij.mermaid.lang.parser

class SequenceTest : MermaidParserTestCase("sequence") {
  fun `test simple sequence`() = doTest(true)

  fun `test sequence with short activations`() = doTest(true)

  fun `test sequence with notes`() = doTest(true)

  fun `test sequence with json formatted link`() = doTest(true)

  fun `test sequence with loop`() = doTest(true)
}
