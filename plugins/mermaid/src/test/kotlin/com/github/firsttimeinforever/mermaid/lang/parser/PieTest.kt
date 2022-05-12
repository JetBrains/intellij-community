package com.github.firsttimeinforever.mermaid.lang.parser

class PieTest : MermaidParserTestCase() {
  fun `test simple pie`() {
    val content = """
    %%Some
    pie showData title Some title
      "some" : 42
      "some" : 42
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >Element(COMMENT)
    >>PsiElement(LINE_COMMENT)
    >>PsiElement(COMMENT_TEXT)
    >PsiElement(EOL)
    >Element(PIE_HEADER)
    >>PsiElement(Pie.PIE)
    >>PsiWhiteSpace
    >>PsiElement(Pie.SHOW_DATA)
    >PsiWhiteSpace
    >Element(PIE_DOCUMENT)
    >>Element(PIE_LINE)
    >>>PsiElement(TITLE)
    >>>PsiWhiteSpace
    >>>PsiElement(TITLE_VALUE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(PIE_LINE)
    >>>Element(STRING)
    >>>>PsiElement(DOUBLE_QUOTE)
    >>>>PsiElement(STRING_VALUE)
    >>>>PsiElement(DOUBLE_QUOTE)
    >>>PsiWhiteSpace
    >>>PsiElement(COLON)
    >>>PsiWhiteSpace
    >>>PsiElement(Pie.VALUE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(PIE_LINE)
    >>>Element(STRING)
    >>>>PsiElement(DOUBLE_QUOTE)
    >>>>PsiElement(STRING_VALUE)
    >>>>PsiElement(DOUBLE_QUOTE)
    >>>PsiWhiteSpace
    >>>PsiElement(COLON)
    >>>PsiWhiteSpace
    >>>PsiElement(Pie.VALUE)
    """.trimIndent()
    doTest(content, expectedTree)
  }
}
