package com.github.firsttimeinforever.mermaid.lang.parser

class JourneyTest : MermaidParserTestCase() {
  fun `test simple journey`() {
    val content = """
    journey
      title My working day
      section    Go to work
        Make tea: 5:   Me
        Go upstairs: 3: Me
        Do work: 1: Me, Cat
      section Go home
        Go downstairs: 5: Me
        Sit down: 5: Me
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(Journey.JOURNEY)
    >Element(JOURNEY_DOCUMENT)
    >>Element(JOURNEY_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(JOURNEY_LINE)
    >>>PsiElement(TITLE)
    >>>PsiWhiteSpace
    >>>PsiElement(TITLE_VALUE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(JOURNEY_LINE)
    >>>PsiElement(Journey.SECTION)
    >>>PsiWhiteSpace
    >>>PsiElement(Journey.SECTION_TITLE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(JOURNEY_LINE)
    >>>PsiElement(Journey.TASK_NAME)
    >>>PsiElement(COLON)
    >>>PsiWhiteSpace
    >>>PsiElement(Journey.TASK_DATA)
    >>>PsiElement(COLON)
    >>>PsiWhiteSpace
    >>>PsiElement(Journey.TASK_DATA)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(JOURNEY_LINE)
    >>>PsiElement(Journey.TASK_NAME)
    >>>PsiElement(COLON)
    >>>PsiWhiteSpace
    >>>PsiElement(Journey.TASK_DATA)
    >>>PsiElement(COLON)
    >>>PsiWhiteSpace
    >>>PsiElement(Journey.TASK_DATA)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(JOURNEY_LINE)
    >>>PsiElement(Journey.TASK_NAME)
    >>>PsiElement(COLON)
    >>>PsiWhiteSpace
    >>>PsiElement(Journey.TASK_DATA)
    >>>PsiElement(COLON)
    >>>PsiWhiteSpace
    >>>PsiElement(Journey.TASK_DATA)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(JOURNEY_LINE)
    >>>PsiElement(Journey.SECTION)
    >>>PsiWhiteSpace
    >>>PsiElement(Journey.SECTION_TITLE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(JOURNEY_LINE)
    >>>PsiElement(Journey.TASK_NAME)
    >>>PsiElement(COLON)
    >>>PsiWhiteSpace
    >>>PsiElement(Journey.TASK_DATA)
    >>>PsiElement(COLON)
    >>>PsiWhiteSpace
    >>>PsiElement(Journey.TASK_DATA)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(JOURNEY_LINE)
    >>>PsiElement(Journey.TASK_NAME)
    >>>PsiElement(COLON)
    >>>PsiWhiteSpace
    >>>PsiElement(Journey.TASK_DATA)
    >>>PsiElement(COLON)
    >>>PsiWhiteSpace
    >>>PsiElement(Journey.TASK_DATA)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test journey with ignored tokens`() {
    val content = """
    journey
      title My working day#123
      section    Go to work#123
        Make tea: 5:   Me#123
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(Journey.JOURNEY)
    >Element(JOURNEY_DOCUMENT)
    >>Element(JOURNEY_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(JOURNEY_LINE)
    >>>PsiElement(TITLE)
    >>>PsiWhiteSpace
    >>>PsiElement(TITLE_VALUE)
    >>PsiElement(IGNORED)
    >>Element(JOURNEY_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(JOURNEY_LINE)
    >>>PsiElement(Journey.SECTION)
    >>>PsiWhiteSpace
    >>>PsiElement(Journey.SECTION_TITLE)
    >>PsiElement(IGNORED)
    >>Element(JOURNEY_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(JOURNEY_LINE)
    >>>PsiElement(Journey.TASK_NAME)
    >>>PsiElement(COLON)
    >>>PsiWhiteSpace
    >>>PsiElement(Journey.TASK_DATA)
    >>>PsiElement(COLON)
    >>>PsiWhiteSpace
    >>>PsiElement(Journey.TASK_DATA)
    >>PsiElement(IGNORED)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test journey with ignored task data`() {
    val content = """
    journey
      title My working day
      section    Go to work
        Make tea: #5:   Me
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(Journey.JOURNEY)
    >Element(JOURNEY_DOCUMENT)
    >>Element(JOURNEY_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(JOURNEY_LINE)
    >>>PsiElement(TITLE)
    >>>PsiWhiteSpace
    >>>PsiElement(TITLE_VALUE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(JOURNEY_LINE)
    >>>PsiElement(Journey.SECTION)
    >>>PsiWhiteSpace
    >>>PsiElement(Journey.SECTION_TITLE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(JOURNEY_LINE)
    >>>PsiElement(Journey.TASK_NAME)
    >>>PsiElement(COLON)
    >>>PsiWhiteSpace
    >>>PsiElement(IGNORED)
    """.trimIndent()
    doTest(content, expectedTree)
  }
}
