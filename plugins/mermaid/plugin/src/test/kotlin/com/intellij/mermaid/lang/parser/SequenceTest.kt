package com.intellij.mermaid.lang.parser

class SequenceTest : MermaidParserTestCase("sequence") {
  fun `test simple sequence`() = doTest(true)

  fun `test sequence with short activations`() = doTest(true)

  fun `test sequence with notes`() = doTest(true)

  fun `test sequence with json formatted link`() = doTest(true)

  fun `test sequence with loop`() = doTest(true)

  fun `test autonumber`() = doTest(true)

  fun `test critical region`() = doTest(true)

  fun `test break`() = doTest(true)

  fun `test box`() = doTest(true)

  fun `test par_over`() = doTest(true)

  fun `test directives`() = doTest(true)

  fun `test entity codes`() = doTest(true)
}
