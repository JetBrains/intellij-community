package com.intellij.mermaid.lang.lexer

class LexerSanityTest: MermaidLexerTestCase() {
  override val diagramName: String
    get() = "common"

  fun `test line comment`() {
    val content = """
    %% This is comment
    """.trimIndent()
    doTest(content)
  }

  fun `test line comment not eating next newline`() {
    val content = """
    %% This is comment
    
    """.trimIndent()
    doTest(content)
  }

  fun `test empty directive`() {
    val content = """
    %%{}%%
    """.trimIndent()
    doTest(content)
  }

  fun `test empty directive with whitespaces`() {
    val content = """
    %%{    }%%
    """.trimIndent()
    doTest(content)
  }

  fun `test directive with single simple numeric property`() {
    val content = """
    %%{ some: 42 }%%
    """.trimIndent()
    doTest(content)
  }

  fun `test directive with single simple quoted property`() {
    val content = """
    %%{ some: "42" }%%
    """.trimIndent()
    doTest(content)
  }

  fun `test directive with multiple simple properties`() {
    val content = """
    %%{ some: "42", other: 42, more: "value" }%%
    """.trimIndent()
    doTest(content)
  }

  fun `test directive with single simple property and whitespaces and newlines`() {
    val content = """
    %%{   some
      
      
      :
       
       
       42
         
         
         }%%
    """.trimIndent()
    doTest(content)
  }

  fun `test packet diagram`() {
    val content = """
    packet-beta
      0-15: "Source Port"
    """.trimIndent()
    doTest(content)
  }

  fun `test architecture diagram`() {
    val content = """
    architecture-beta
      group api(cloud)[API]
    """.trimIndent()
    doTest(content)
  }

  fun `test kanban diagram`() {
    val content = """
    kanban
      Todo
        [Create documentation]
    """.trimIndent()
    doTest(content)
  }
}
