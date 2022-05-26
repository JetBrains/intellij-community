package com.github.firsttimeinforever.mermaid.lang.parser

class StateTest : MermaidParserTestCase() {
  fun `test different state definition`() {
    val content = """
    stateDiagram-v2
      s1
      state "This is a state description" as s2
      s2 : This is a state description
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(STATE_DIAGRAM)
    >Element(STATE_DOCUMENT)
    >>Element(STATE_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>PsiElement(STATE)
    >>>PsiWhiteSpace
    >>>Element(DESCRIPTION)
    >>>>Element(STRING)
    >>>>>PsiElement(DOUBLE_QUOTE)
    >>>>>PsiElement(STRING_VALUE)
    >>>>>PsiElement(DOUBLE_QUOTE)
    >>>PsiWhiteSpace
    >>>PsiElement(AS)
    >>>PsiWhiteSpace
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(COLON)
    >>>PsiElement(LABEL)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test transitions`() {
    val content = """
    stateDiagram-v2
      s1 --> s2
      s3 --> s4: A transition
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(STATE_DIAGRAM)
    >Element(STATE_DOCUMENT)
    >>Element(STATE_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(ARROW)
    >>>PsiWhiteSpace
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(ARROW)
    >>>PsiWhiteSpace
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiElement(COLON)
    >>>PsiElement(LABEL)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test special start and end states`() {
    val content = """
    stateDiagram-v2
      [*] --> s1
      s1 --> [*]
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(STATE_DIAGRAM)
    >Element(STATE_DOCUMENT)
    >>Element(STATE_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>Element(STATE_ID)
    >>>>PsiElement(OPEN_SQUARE)
    >>>>PsiElement(STAR)
    >>>>PsiElement(CLOSE_SQUARE)
    >>>PsiWhiteSpace
    >>>PsiElement(ARROW)
    >>>PsiWhiteSpace
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(ARROW)
    >>>PsiWhiteSpace
    >>>Element(STATE_ID)
    >>>>PsiElement(OPEN_SQUARE)
    >>>>PsiElement(STAR)
    >>>>PsiElement(CLOSE_SQUARE)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test composite states`() {
    val content = """
    stateDiagram-v2
      [*] --> First
      state First {
        [*] --> Second
        state Second {
          [*] --> second
          second --> Third   
        }
        state Third {
          [*] --> third
          third --> [*]
        }
      }
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(STATE_DIAGRAM)
    >Element(STATE_DOCUMENT)
    >>Element(STATE_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>Element(STATE_ID)
    >>>>PsiElement(OPEN_SQUARE)
    >>>>PsiElement(STAR)
    >>>>PsiElement(CLOSE_SQUARE)
    >>>PsiWhiteSpace
    >>>PsiElement(ARROW)
    >>>PsiWhiteSpace
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>PsiElement(STATE)
    >>>PsiWhiteSpace
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(OPEN_CURLY)
    >>>Element(INNER_STATE_DOCUMENT)
    >>>>Element(INNER_STATE_LINE)
    >>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>Element(INNER_STATE_LINE)
    >>>>>Element(STATE_ID)
    >>>>>>PsiElement(OPEN_SQUARE)
    >>>>>>PsiElement(STAR)
    >>>>>>PsiElement(CLOSE_SQUARE)
    >>>>>PsiWhiteSpace
    >>>>>PsiElement(ARROW)
    >>>>>PsiWhiteSpace
    >>>>>Element(STATE_ID)
    >>>>>>PsiElement(ID)
    >>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>Element(INNER_STATE_LINE)
    >>>>>PsiElement(STATE)
    >>>>>PsiWhiteSpace
    >>>>>Element(STATE_ID)
    >>>>>>PsiElement(ID)
    >>>>>PsiWhiteSpace
    >>>>>PsiElement(OPEN_CURLY)
    >>>>>Element(INNER_STATE_DOCUMENT)
    >>>>>>Element(INNER_STATE_LINE)
    >>>>>>>PsiElement(EOL)
    >>>>>>PsiWhiteSpace
    >>>>>>Element(INNER_STATE_LINE)
    >>>>>>>Element(STATE_ID)
    >>>>>>>>PsiElement(OPEN_SQUARE)
    >>>>>>>>PsiElement(STAR)
    >>>>>>>>PsiElement(CLOSE_SQUARE)
    >>>>>>>PsiWhiteSpace
    >>>>>>>PsiElement(ARROW)
    >>>>>>>PsiWhiteSpace
    >>>>>>>Element(STATE_ID)
    >>>>>>>>PsiElement(ID)
    >>>>>>>PsiElement(EOL)
    >>>>>>PsiWhiteSpace
    >>>>>>Element(INNER_STATE_LINE)
    >>>>>>>Element(STATE_ID)
    >>>>>>>>PsiElement(ID)
    >>>>>>>PsiWhiteSpace
    >>>>>>>PsiElement(ARROW)
    >>>>>>>PsiWhiteSpace
    >>>>>>>Element(STATE_ID)
    >>>>>>>>PsiElement(ID)
    >>>>>>>PsiWhiteSpace
    >>>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>PsiElement(CLOSE_CURLY)
    >>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>Element(INNER_STATE_LINE)
    >>>>>PsiElement(STATE)
    >>>>>PsiWhiteSpace
    >>>>>Element(STATE_ID)
    >>>>>>PsiElement(ID)
    >>>>>PsiWhiteSpace
    >>>>>PsiElement(OPEN_CURLY)
    >>>>>Element(INNER_STATE_DOCUMENT)
    >>>>>>Element(INNER_STATE_LINE)
    >>>>>>>PsiElement(EOL)
    >>>>>>PsiWhiteSpace
    >>>>>>Element(INNER_STATE_LINE)
    >>>>>>>Element(STATE_ID)
    >>>>>>>>PsiElement(OPEN_SQUARE)
    >>>>>>>>PsiElement(STAR)
    >>>>>>>>PsiElement(CLOSE_SQUARE)
    >>>>>>>PsiWhiteSpace
    >>>>>>>PsiElement(ARROW)
    >>>>>>>PsiWhiteSpace
    >>>>>>>Element(STATE_ID)
    >>>>>>>>PsiElement(ID)
    >>>>>>>PsiElement(EOL)
    >>>>>>PsiWhiteSpace
    >>>>>>Element(INNER_STATE_LINE)
    >>>>>>>Element(STATE_ID)
    >>>>>>>>PsiElement(ID)
    >>>>>>>PsiWhiteSpace
    >>>>>>>PsiElement(ARROW)
    >>>>>>>PsiWhiteSpace
    >>>>>>>Element(STATE_ID)
    >>>>>>>>PsiElement(OPEN_SQUARE)
    >>>>>>>>PsiElement(STAR)
    >>>>>>>>PsiElement(CLOSE_SQUARE)
    >>>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>PsiElement(CLOSE_CURLY)
    >>>>>PsiElement(EOL)
    >>>PsiWhiteSpace
    >>>PsiElement(CLOSE_CURLY)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test state annotation`() {
    val content = """
    stateDiagram-v2
      state if_state <<choice>>
      [*] --> IsPositive
      IsPositive --> if_state
      
      state fork_state <<fork>>
      [*] --> fork_state

      state join_state <<join>>
      join_state --> State4
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(STATE_DIAGRAM)
    >Element(STATE_DOCUMENT)
    >>Element(STATE_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>PsiElement(STATE)
    >>>PsiWhiteSpace
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(ANNOTATION_START)
    >>>PsiElement(ANNOTATION_VALUE)
    >>>PsiElement(ANNOTATION_END)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>Element(STATE_ID)
    >>>>PsiElement(OPEN_SQUARE)
    >>>>PsiElement(STAR)
    >>>>PsiElement(CLOSE_SQUARE)
    >>>PsiWhiteSpace
    >>>PsiElement(ARROW)
    >>>PsiWhiteSpace
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(ARROW)
    >>>PsiWhiteSpace
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>PsiElement(STATE)
    >>>PsiWhiteSpace
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(ANNOTATION_START)
    >>>PsiElement(ANNOTATION_VALUE)
    >>>PsiElement(ANNOTATION_END)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>Element(STATE_ID)
    >>>>PsiElement(OPEN_SQUARE)
    >>>>PsiElement(STAR)
    >>>>PsiElement(CLOSE_SQUARE)
    >>>PsiWhiteSpace
    >>>PsiElement(ARROW)
    >>>PsiWhiteSpace
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>Element(STATE_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>PsiElement(STATE)
    >>>PsiWhiteSpace
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(ANNOTATION_START)
    >>>PsiElement(ANNOTATION_VALUE)
    >>>PsiElement(ANNOTATION_END)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(ARROW)
    >>>PsiWhiteSpace
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test notes`() {
    val content = """
    stateDiagram-v2
      State1: The state with a note
      note right of State1
          Important information! You can write
          notes.
      end note
      State1 --> State2
      note left of State2 : This is the note to the left.
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(STATE_DIAGRAM)
    >Element(STATE_DOCUMENT)
    >>Element(STATE_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiElement(COLON)
    >>>PsiElement(LABEL)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>PsiElement(NOTE)
    >>>PsiWhiteSpace
    >>>PsiElement(RIGHT_OF)
    >>>PsiWhiteSpace
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>>PsiWhiteSpace
    >>>PsiElement(NOTE_CONTENT)
    >>>PsiElement(EOL)
    >>>PsiWhiteSpace
    >>>PsiElement(NOTE_CONTENT)
    >>>PsiElement(EOL)
    >>>PsiWhiteSpace
    >>>PsiElement(END)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(ARROW)
    >>>PsiWhiteSpace
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>PsiElement(NOTE)
    >>>PsiWhiteSpace
    >>>PsiElement(LEFT_OF)
    >>>PsiWhiteSpace
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(COLON)
    >>>PsiWhiteSpace
    >>>PsiElement(NOTE_CONTENT)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test diagram with concurrency`() {
    val content = """
    stateDiagram-v2
      [*] --> A
  
      state A {
        [*] --> B
        --
        [*] --> C
        --
        [*] --> D
      }
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(STATE_DIAGRAM)
    >Element(STATE_DOCUMENT)
    >>Element(STATE_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>Element(STATE_ID)
    >>>>PsiElement(OPEN_SQUARE)
    >>>>PsiElement(STAR)
    >>>>PsiElement(CLOSE_SQUARE)
    >>>PsiWhiteSpace
    >>>PsiElement(ARROW)
    >>>PsiWhiteSpace
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>Element(STATE_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>PsiElement(STATE)
    >>>PsiWhiteSpace
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(OPEN_CURLY)
    >>>Element(INNER_STATE_DOCUMENT)
    >>>>Element(INNER_STATE_LINE)
    >>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>Element(INNER_STATE_LINE)
    >>>>>Element(STATE_ID)
    >>>>>>PsiElement(OPEN_SQUARE)
    >>>>>>PsiElement(STAR)
    >>>>>>PsiElement(CLOSE_SQUARE)
    >>>>>PsiWhiteSpace
    >>>>>PsiElement(ARROW)
    >>>>>PsiWhiteSpace
    >>>>>Element(STATE_ID)
    >>>>>>PsiElement(ID)
    >>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>Element(INNER_STATE_LINE)
    >>>>>PsiElement(MINUS)
    >>>>>PsiElement(MINUS)
    >>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>Element(INNER_STATE_LINE)
    >>>>>Element(STATE_ID)
    >>>>>>PsiElement(OPEN_SQUARE)
    >>>>>>PsiElement(STAR)
    >>>>>>PsiElement(CLOSE_SQUARE)
    >>>>>PsiWhiteSpace
    >>>>>PsiElement(ARROW)
    >>>>>PsiWhiteSpace
    >>>>>Element(STATE_ID)
    >>>>>>PsiElement(ID)
    >>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>Element(INNER_STATE_LINE)
    >>>>>PsiElement(MINUS)
    >>>>>PsiElement(MINUS)
    >>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>Element(INNER_STATE_LINE)
    >>>>>Element(STATE_ID)
    >>>>>>PsiElement(OPEN_SQUARE)
    >>>>>>PsiElement(STAR)
    >>>>>>PsiElement(CLOSE_SQUARE)
    >>>>>PsiWhiteSpace
    >>>>>PsiElement(ARROW)
    >>>>>PsiWhiteSpace
    >>>>>Element(STATE_ID)
    >>>>>>PsiElement(ID)
    >>>>>PsiElement(EOL)
    >>>PsiWhiteSpace
    >>>PsiElement(CLOSE_CURLY)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test direction`() {
    val content = """
    stateDiagram
      direction LR
      A --> B
      state B {
        direction LR
        a --> b
      }
      B --> D
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(STATE_DIAGRAM)
    >Element(STATE_DOCUMENT)
    >>Element(STATE_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>PsiElement(DIRECTION)
    >>>PsiWhiteSpace
    >>>PsiElement(DIR)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(ARROW)
    >>>PsiWhiteSpace
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>PsiElement(STATE)
    >>>PsiWhiteSpace
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(OPEN_CURLY)
    >>>Element(INNER_STATE_DOCUMENT)
    >>>>Element(INNER_STATE_LINE)
    >>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>Element(INNER_STATE_LINE)
    >>>>>PsiElement(DIRECTION)
    >>>>>PsiWhiteSpace
    >>>>>PsiElement(DIR)
    >>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>Element(INNER_STATE_LINE)
    >>>>>Element(STATE_ID)
    >>>>>>PsiElement(ID)
    >>>>>PsiWhiteSpace
    >>>>>PsiElement(ARROW)
    >>>>>PsiWhiteSpace
    >>>>>Element(STATE_ID)
    >>>>>>PsiElement(ID)
    >>>>>PsiElement(EOL)
    >>>PsiWhiteSpace
    >>>PsiElement(CLOSE_CURLY)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(ARROW)
    >>>PsiWhiteSpace
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test comments`() {
    val content = """
    %% this is a comment
    stateDiagram
      A --> B %% this is a comment
      %% this is a comment
      state B {
        a --> b %% this is a comment
      }
      %% this is a comment
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >Element(COMMENT)
    >>PsiElement(LINE_COMMENT)
    >>PsiElement(COMMENT_TEXT)
    >PsiElement(EOL)
    >PsiElement(STATE_DIAGRAM)
    >Element(STATE_DOCUMENT)
    >>Element(STATE_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(ARROW)
    >>>PsiWhiteSpace
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>Element(COMMENT)
    >>>>PsiElement(LINE_COMMENT)
    >>>>PsiElement(COMMENT_TEXT)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>Element(COMMENT)
    >>>>PsiElement(LINE_COMMENT)
    >>>>PsiElement(COMMENT_TEXT)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>PsiElement(STATE)
    >>>PsiWhiteSpace
    >>>Element(STATE_ID)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(OPEN_CURLY)
    >>>Element(INNER_STATE_DOCUMENT)
    >>>>Element(INNER_STATE_LINE)
    >>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>Element(INNER_STATE_LINE)
    >>>>>Element(STATE_ID)
    >>>>>>PsiElement(ID)
    >>>>>PsiWhiteSpace
    >>>>>PsiElement(ARROW)
    >>>>>PsiWhiteSpace
    >>>>>Element(STATE_ID)
    >>>>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>Element(INNER_STATE_LINE)
    >>>>>Element(COMMENT)
    >>>>>>PsiElement(LINE_COMMENT)
    >>>>>>PsiElement(COMMENT_TEXT)
    >>>>>PsiElement(EOL)
    >>>PsiWhiteSpace
    >>>PsiElement(CLOSE_CURLY)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(STATE_LINE)
    >>>Element(COMMENT)
    >>>>PsiElement(LINE_COMMENT)
    >>>>PsiElement(COMMENT_TEXT)
    """.trimIndent()
    doTest(content, expectedTree)
  }
}
