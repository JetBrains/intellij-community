package com.github.firsttimeinforever.mermaid.lang.parser

class GitGraphTest : MermaidParserTestCase() {
  fun `test simple git graph`() {
    val content = """
    gitGraph
     commit
     commit
     branch develop
     checkout develop
     commit
     commit
     checkout main
     merge develop
     commit
     commit
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(GIT_GRAPH)
    >Element(GIT_GRAPH_DOCUMENT)
    >>Element(GIT_GRAPH_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(GIT_GRAPH_LINE)
    >>>Element(COMMIT_STATEMENT)
    >>>>PsiElement(COMMIT)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(GIT_GRAPH_LINE)
    >>>Element(COMMIT_STATEMENT)
    >>>>PsiElement(COMMIT)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(GIT_GRAPH_LINE)
    >>>Element(BRANCH_STATEMENT)
    >>>>PsiElement(BRANCH)
    >>>>PsiWhiteSpace
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(GIT_GRAPH_LINE)
    >>>Element(CHECKOUT_STATEMENT)
    >>>>PsiElement(CHECKOUT)
    >>>>PsiWhiteSpace
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(GIT_GRAPH_LINE)
    >>>Element(COMMIT_STATEMENT)
    >>>>PsiElement(COMMIT)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(GIT_GRAPH_LINE)
    >>>Element(COMMIT_STATEMENT)
    >>>>PsiElement(COMMIT)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(GIT_GRAPH_LINE)
    >>>Element(CHECKOUT_STATEMENT)
    >>>>PsiElement(CHECKOUT)
    >>>>PsiWhiteSpace
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(GIT_GRAPH_LINE)
    >>>Element(MERGE_STATEMENT)
    >>>>PsiElement(MERGE)
    >>>>PsiWhiteSpace
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(GIT_GRAPH_LINE)
    >>>Element(COMMIT_STATEMENT)
    >>>>PsiElement(COMMIT)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(GIT_GRAPH_LINE)
    >>>Element(COMMIT_STATEMENT)
    >>>>PsiElement(COMMIT)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test git graph with custom commit id`() {
    val content = """
    gitGraph
      commit id: "Alpha"
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(GIT_GRAPH)
    >Element(GIT_GRAPH_DOCUMENT)
    >>Element(GIT_GRAPH_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(GIT_GRAPH_LINE)
    >>>Element(COMMIT_STATEMENT)
    >>>>PsiElement(COMMIT)
    >>>>PsiWhiteSpace
    >>>>Element(COMMIT_ID_ATTRIBUTE)
    >>>>>PsiElement(ID_KEYWORD)
    >>>>>PsiElement(COLON)
    >>>>>PsiWhiteSpace
    >>>>>Element(STRING)
    >>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>>>PsiElement(STRING_VALUE)
    >>>>>>PsiElement(DOUBLE_QUOTE)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test commit type`() {
    val content = """
    gitGraph
      commit id: "Normal"
      commit id: "Reverse" type: REVERSE
      commit type: HIGHLIGHT
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(GIT_GRAPH)
    >Element(GIT_GRAPH_DOCUMENT)
    >>Element(GIT_GRAPH_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(GIT_GRAPH_LINE)
    >>>Element(COMMIT_STATEMENT)
    >>>>PsiElement(COMMIT)
    >>>>PsiWhiteSpace
    >>>>Element(COMMIT_ID_ATTRIBUTE)
    >>>>>PsiElement(ID_KEYWORD)
    >>>>>PsiElement(COLON)
    >>>>>PsiWhiteSpace
    >>>>>Element(STRING)
    >>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>>>PsiElement(STRING_VALUE)
    >>>>>>PsiElement(DOUBLE_QUOTE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(GIT_GRAPH_LINE)
    >>>Element(COMMIT_STATEMENT)
    >>>>PsiElement(COMMIT)
    >>>>PsiWhiteSpace
    >>>>Element(COMMIT_ID_ATTRIBUTE)
    >>>>>PsiElement(ID_KEYWORD)
    >>>>>PsiElement(COLON)
    >>>>>PsiWhiteSpace
    >>>>>Element(STRING)
    >>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>>>PsiElement(STRING_VALUE)
    >>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>PsiWhiteSpace
    >>>>Element(COMMIT_TYPE_ATTRIBUTE)
    >>>>>PsiElement(TYPE)
    >>>>>PsiElement(COLON)
    >>>>>PsiWhiteSpace
    >>>>>PsiElement(REVERSE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(GIT_GRAPH_LINE)
    >>>Element(COMMIT_STATEMENT)
    >>>>PsiElement(COMMIT)
    >>>>PsiWhiteSpace
    >>>>Element(COMMIT_TYPE_ATTRIBUTE)
    >>>>>PsiElement(TYPE)
    >>>>>PsiElement(COLON)
    >>>>>PsiWhiteSpace
    >>>>>PsiElement(HIGHLIGHT)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test commit tags`() {
    val content = """
    gitGraph
      commit tag: "v1.0.0"
      commit id: "Reverse" type: REVERSE tag: "RC_1"
      commit tag: "8.8.4" type: HIGHLIGHT id: "Highlight"
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(GIT_GRAPH)
    >Element(GIT_GRAPH_DOCUMENT)
    >>Element(GIT_GRAPH_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(GIT_GRAPH_LINE)
    >>>Element(COMMIT_STATEMENT)
    >>>>PsiElement(COMMIT)
    >>>>PsiWhiteSpace
    >>>>Element(COMMIT_TAG_ATTRIBUTE)
    >>>>>PsiElement(TAG)
    >>>>>PsiElement(COLON)
    >>>>>PsiWhiteSpace
    >>>>>Element(STRING)
    >>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>>>PsiElement(STRING_VALUE)
    >>>>>>PsiElement(DOUBLE_QUOTE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(GIT_GRAPH_LINE)
    >>>Element(COMMIT_STATEMENT)
    >>>>PsiElement(COMMIT)
    >>>>PsiWhiteSpace
    >>>>Element(COMMIT_ID_ATTRIBUTE)
    >>>>>PsiElement(ID_KEYWORD)
    >>>>>PsiElement(COLON)
    >>>>>PsiWhiteSpace
    >>>>>Element(STRING)
    >>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>>>PsiElement(STRING_VALUE)
    >>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>PsiWhiteSpace
    >>>>Element(COMMIT_TYPE_ATTRIBUTE)
    >>>>>PsiElement(TYPE)
    >>>>>PsiElement(COLON)
    >>>>>PsiWhiteSpace
    >>>>>PsiElement(REVERSE)
    >>>>PsiWhiteSpace
    >>>>Element(COMMIT_TAG_ATTRIBUTE)
    >>>>>PsiElement(TAG)
    >>>>>PsiElement(COLON)
    >>>>>PsiWhiteSpace
    >>>>>Element(STRING)
    >>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>>>PsiElement(STRING_VALUE)
    >>>>>>PsiElement(DOUBLE_QUOTE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(GIT_GRAPH_LINE)
    >>>Element(COMMIT_STATEMENT)
    >>>>PsiElement(COMMIT)
    >>>>PsiWhiteSpace
    >>>>Element(COMMIT_TAG_ATTRIBUTE)
    >>>>>PsiElement(TAG)
    >>>>>PsiElement(COLON)
    >>>>>PsiWhiteSpace
    >>>>>Element(STRING)
    >>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>>>PsiElement(STRING_VALUE)
    >>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>PsiWhiteSpace
    >>>>Element(COMMIT_TYPE_ATTRIBUTE)
    >>>>>PsiElement(TYPE)
    >>>>>PsiElement(COLON)
    >>>>>PsiWhiteSpace
    >>>>>PsiElement(HIGHLIGHT)
    >>>>PsiWhiteSpace
    >>>>Element(COMMIT_ID_ATTRIBUTE)
    >>>>>PsiElement(ID_KEYWORD)
    >>>>>PsiElement(COLON)
    >>>>>PsiWhiteSpace
    >>>>>Element(STRING)
    >>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>>>PsiElement(STRING_VALUE)
    >>>>>>PsiElement(DOUBLE_QUOTE)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test cherry pick`() {
    val content = """
    gitGraph
      cherry-pick id : "A"
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(GIT_GRAPH)
    >Element(GIT_GRAPH_DOCUMENT)
    >>Element(GIT_GRAPH_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(GIT_GRAPH_LINE)
    >>>Element(CHERRY_PICK_STATEMENT)
    >>>>PsiElement(CHERRY_PICK)
    >>>>PsiWhiteSpace
    >>>>Element(COMMIT_ID_ATTRIBUTE)
    >>>>>PsiElement(ID_KEYWORD)
    >>>>>PsiWhiteSpace
    >>>>>PsiElement(COLON)
    >>>>>PsiWhiteSpace
    >>>>>Element(STRING)
    >>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>>>PsiElement(STRING_VALUE)
    >>>>>>PsiElement(DOUBLE_QUOTE)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test order`() {
    val content = """
    gitGraph
      branch test1 order: 1
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(GIT_GRAPH)
    >Element(GIT_GRAPH_DOCUMENT)
    >>Element(GIT_GRAPH_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(GIT_GRAPH_LINE)
    >>>Element(BRANCH_STATEMENT)
    >>>>PsiElement(BRANCH)
    >>>>PsiWhiteSpace
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>Element(BRANCH_ORDER)
    >>>>>PsiElement(ORDER)
    >>>>>PsiElement(COLON)
    >>>>>PsiWhiteSpace
    >>>>>PsiElement(NUM)
    """.trimIndent()
    doTest(content, expectedTree)
  }
}
