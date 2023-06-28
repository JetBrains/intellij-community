package com.intellij.mermaid.lang.lexer

class ZenUMLTest : MermaidLexerTestCase() {
  override val diagramName: String
    get() = "zenUML"

  fun `test zenUML`() {
    val content = """
    zenuml
      title Demo
      Alice->John: Hello John, how are you?
      John->Alice: Great!
      Alice->John: See you later!
    """.trimIndent()
    doTest(content)
  }
}
