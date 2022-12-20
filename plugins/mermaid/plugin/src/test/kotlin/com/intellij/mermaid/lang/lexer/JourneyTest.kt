package com.intellij.mermaid.lang.lexer

class JourneyTest : MermaidLexerTestCase() {
  override val diagramName: String
    get() = "journey"

  fun `test simple journey title and section title`() {
    val content = """
    journey
      title My working day
      section Go to work
    """.trimIndent()
    doTest(content)
  }

  fun `test journey title and section title with whitespaces`() {
    val content = """
    journey
      title     My working day
      section       Go to work
    """.trimIndent()
    doTest(content)
  }

  fun `test journey title and section title with whitespaces and sharp`() {
    val content = """
    journey
      title     My working# day
      section       Go to# work
    
    """.trimIndent()
    doTest(content)
  }

  fun `test journey with one section and tasks`() {
    val content = """
    journey
      title My working day
      section Go to work
        Make tea: 5: Me
        Go upstairs: 3: Me
    """.trimIndent()
    doTest(content)
  }

  fun `test journey with sharp after task`() {
    val content = """
    journey
      title My working day
      section Go to work
        Make tea: 5: Me#123
    """.trimIndent()
    doTest(content)
  }

  fun `test journey with sharp in task`() {
    val content = """
    journey
      title My working day
      section Go to work
        Make tea: #5: Me
    """.trimIndent()
    doTest(content)
  }

  fun `test journey with two sections`() {
    val content = """
    journey
      title My working day
      section Go to work
        Make tea: 5: Me
      section Go home
        Go downstairs: 5: Me
    """.trimIndent()
    doTest(content)
  }

  fun `test journey task with whitespaces`() {
    val content = """
    journey
      title My working day
      section Go to work
            Make tea  :    5   :   Me
    """.trimIndent()
    doTest(content)
  }

  fun `test journey`() {
    val content = """
    journey
      title My working day
      section Go to work
         : Make tea: 5: Me
    """.trimIndent()
    doTest(content)
  }

  fun `test journey with comments`() {
    val content = """
    journey %% This is comment 
      title My working day %% This is not comment
      section Go to work %% This is not comment
        Make tea: 5: Me %% This is not comment
        %% This is comment
    """.trimIndent()
    doTest(content)
  }
}
