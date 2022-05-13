package com.github.firsttimeinforever.mermaid.lang.parser


class SequenceTest : MermaidParserTestCase() {
  fun `test simple sequence`() {
    val content = """
    sequenceDiagram
      participant A B as Alice B
      actor J as John
      A B --> J: Hello John, how are you? ; J -->> A B : Great! # And you? J -->> A B
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(Sequence.SEQUENCE)
    >Element(SEQUENCE_DOCUMENT)
    >>Element(SEQUENCE_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(SEQUENCE_LINE)
    >>>PsiElement(Sequence.PARTICIPANT)
    >>>PsiWhiteSpace
    >>>Element(IDENTIFIER)
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(Sequence.AS)
    >>>PsiWhiteSpace
    >>>Element(ID_ALIAS)
    >>>>PsiElement(ALIAS)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ALIAS)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(SEQUENCE_LINE)
    >>>PsiElement(Sequence.ACTOR)
    >>>PsiWhiteSpace
    >>>Element(IDENTIFIER)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(Sequence.AS)
    >>>PsiWhiteSpace
    >>>Element(ID_ALIAS)
    >>>>PsiElement(ALIAS)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(SEQUENCE_LINE)
    >>>Element(SIGNAL_STATEMENT)
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>>PsiWhiteSpace
    >>>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>Element(SIGNAL_TYPE)
    >>>>>PsiElement(Sequence.DOTTED_OPEN_ARROW)
    >>>>PsiWhiteSpace
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiElement(COLON)
    >>>>PsiElement(Sequence.MESSAGE)
    >>>PsiElement(SEMICOLON)
    >>PsiWhiteSpace
    >>Element(SEQUENCE_LINE)
    >>>Element(SIGNAL_STATEMENT)
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>Element(SIGNAL_TYPE)
    >>>>>PsiElement(Sequence.DOTTED_ARROW)
    >>>>PsiWhiteSpace
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>>PsiWhiteSpace
    >>>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>PsiElement(COLON)
    >>>>PsiElement(Sequence.MESSAGE)
    >>PsiElement(IGNORED)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test sequence with short activations`() {
    val content = """
    sequenceDiagram
      Alice->>+John: Hello John, how are you?
      John-->>-Alice: Great!
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(Sequence.SEQUENCE)
    >Element(SEQUENCE_DOCUMENT)
    >>Element(SEQUENCE_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(SEQUENCE_LINE)
    >>>Element(SIGNAL_STATEMENT)
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>Element(SIGNAL_TYPE)
    >>>>>PsiElement(Sequence.SOLID_ARROW)
    >>>>PsiElement(PLUS)
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiElement(COLON)
    >>>>PsiElement(Sequence.MESSAGE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(SEQUENCE_LINE)
    >>>Element(SIGNAL_STATEMENT)
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>Element(SIGNAL_TYPE)
    >>>>>PsiElement(Sequence.DOTTED_ARROW)
    >>>>PsiElement(MINUS)
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiElement(COLON)
    >>>>PsiElement(Sequence.MESSAGE)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test sequence with notes`() {
    val content = """
    sequenceDiagram
      participant John
      Note right of John: Text in note
      Alice->John: Hello John, how are you?
      Note over Alice,John: A typical interaction
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(Sequence.SEQUENCE)
    >Element(SEQUENCE_DOCUMENT)
    >>Element(SEQUENCE_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(SEQUENCE_LINE)
    >>>PsiElement(Sequence.PARTICIPANT)
    >>>PsiWhiteSpace
    >>>Element(IDENTIFIER)
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(SEQUENCE_LINE)
    >>>Element(NOTE_STATEMENT)
    >>>>PsiElement(Sequence.NOTE)
    >>>>PsiWhiteSpace
    >>>>PsiElement(Sequence.RIGHT_OF)
    >>>>PsiWhiteSpace
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiElement(COLON)
    >>>>PsiElement(Sequence.MESSAGE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(SEQUENCE_LINE)
    >>>Element(SIGNAL_STATEMENT)
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>Element(SIGNAL_TYPE)
    >>>>>PsiElement(Sequence.SOLID_OPEN_ARROW)
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiElement(COLON)
    >>>>PsiElement(Sequence.MESSAGE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(SEQUENCE_LINE)
    >>>Element(NOTE_STATEMENT)
    >>>>PsiElement(Sequence.NOTE)
    >>>>PsiWhiteSpace
    >>>>PsiElement(Sequence.OVER)
    >>>>PsiWhiteSpace
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiElement(COMMA)
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiElement(COLON)
    >>>>PsiElement(Sequence.MESSAGE)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test sequence with json formatted link`() {
    val content = """
    sequenceDiagram
      participant Alice
      links Alice: {"Dashboard": "https://dashboard.contoso.com/alice", "Wiki": "https://wiki.contoso.com/alice"}
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(Sequence.SEQUENCE)
    >Element(SEQUENCE_DOCUMENT)
    >>Element(SEQUENCE_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(SEQUENCE_LINE)
    >>>PsiElement(Sequence.PARTICIPANT)
    >>>PsiWhiteSpace
    >>>Element(IDENTIFIER)
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(SEQUENCE_LINE)
    >>>Element(LINKS_STATEMENT)
    >>>>PsiElement(Sequence.LINKS)
    >>>>PsiWhiteSpace
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiElement(COLON)
    >>>>PsiWhiteSpace
    >>>>Element(LINKS_VALUES)
    >>>>>PsiElement(OPEN_CURLY)
    >>>>>Element(STRING)
    >>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>>>PsiElement(STRING_VALUE)
    >>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>>PsiElement(COLON)
    >>>>>PsiWhiteSpace
    >>>>>Element(STRING)
    >>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>>>PsiElement(STRING_VALUE)
    >>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>>PsiElement(COMMA)
    >>>>>PsiWhiteSpace
    >>>>>Element(STRING)
    >>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>>>PsiElement(STRING_VALUE)
    >>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>>PsiElement(COLON)
    >>>>>PsiWhiteSpace
    >>>>>Element(STRING)
    >>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>>>PsiElement(STRING_VALUE)
    >>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>>PsiElement(CLOSE_CURLY)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test sequence with loop`() {
    val content = """
    sequenceDiagram
      Alice->John: Hello John, how are you?
      loop Every minute
          participant Bob; John-->Alice: Great!; Alice -> Bob: WOWO
      end
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(Sequence.SEQUENCE)
    >Element(SEQUENCE_DOCUMENT)
    >>Element(SEQUENCE_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(SEQUENCE_LINE)
    >>>Element(SIGNAL_STATEMENT)
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>Element(SIGNAL_TYPE)
    >>>>>PsiElement(Sequence.SOLID_OPEN_ARROW)
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiElement(COLON)
    >>>>PsiElement(Sequence.MESSAGE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(SEQUENCE_LINE)
    >>>PsiElement(Sequence.LOOP)
    >>>PsiElement(Sequence.MESSAGE)
    >>>Element(SEQUENCE_DOCUMENT)
    >>>>Element(SEQUENCE_LINE)
    >>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>Element(SEQUENCE_LINE)
    >>>>>PsiElement(Sequence.PARTICIPANT)
    >>>>>PsiWhiteSpace
    >>>>>Element(IDENTIFIER)
    >>>>>>PsiElement(ID)
    >>>>>PsiElement(SEMICOLON)
    >>>>PsiWhiteSpace
    >>>>Element(SEQUENCE_LINE)
    >>>>>Element(SIGNAL_STATEMENT)
    >>>>>>Element(IDENTIFIER)
    >>>>>>>PsiElement(ID)
    >>>>>>Element(SIGNAL_TYPE)
    >>>>>>>PsiElement(Sequence.DOTTED_OPEN_ARROW)
    >>>>>>Element(IDENTIFIER)
    >>>>>>>PsiElement(ID)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiElement(Sequence.MESSAGE)
    >>>>>PsiElement(SEMICOLON)
    >>>>PsiWhiteSpace
    >>>>Element(SEQUENCE_LINE)
    >>>>>Element(SIGNAL_STATEMENT)
    >>>>>>Element(IDENTIFIER)
    >>>>>>>PsiElement(ID)
    >>>>>>PsiWhiteSpace
    >>>>>>Element(SIGNAL_TYPE)
    >>>>>>>PsiElement(Sequence.SOLID_OPEN_ARROW)
    >>>>>>PsiWhiteSpace
    >>>>>>Element(IDENTIFIER)
    >>>>>>>PsiElement(ID)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiElement(Sequence.MESSAGE)
    >>>>>PsiElement(EOL)
    >>>PsiWhiteSpace
    >>>PsiElement(END)
    """.trimIndent()
    doTest(content, expectedTree)
  }
}
