package com.github.firsttimeinforever.mermaid.lang.parser

class EntityRelationshipTest : MermaidParserTestCase() {
  fun `test simple entity relationship`() {
    val content = """
    erDiagram
      CUSTOMER ||--o{ ORDER : places
      ORDER ||--|{ LINE-ITEM : contains
      CUSTOMER }|..|{ DELIVERY-ADDRESS : uses
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(ENTITY_RELATIONSHIP)
    >Element(ER_DOCUMENT)
    >>Element(ER_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(ER_LINE)
    >>>Element(IDENTIFIER)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>Element(RELATIONSHIP)
    >>>>PsiElement(ONLY_ONE)
    >>>>PsiElement(IDENTIFYING)
    >>>>PsiElement(ZERO_OR_MORE_RIGHT)
    >>>PsiWhiteSpace
    >>>Element(IDENTIFIER)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(COLON)
    >>>PsiWhiteSpace
    >>>PsiElement(LABEL)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(ER_LINE)
    >>>Element(IDENTIFIER)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>Element(RELATIONSHIP)
    >>>>PsiElement(ONLY_ONE)
    >>>>PsiElement(IDENTIFYING)
    >>>>PsiElement(ONE_OR_MORE_RIGHT)
    >>>PsiWhiteSpace
    >>>Element(IDENTIFIER)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(COLON)
    >>>PsiWhiteSpace
    >>>PsiElement(LABEL)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(ER_LINE)
    >>>Element(IDENTIFIER)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>Element(RELATIONSHIP)
    >>>>PsiElement(ONE_OR_MORE_LEFT)
    >>>>PsiElement(NON_IDENTIFYING)
    >>>>PsiElement(ONE_OR_MORE_RIGHT)
    >>>PsiWhiteSpace
    >>>Element(IDENTIFIER)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(COLON)
    >>>PsiWhiteSpace
    >>>PsiElement(LABEL)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test entity with attributes`() {
    val content = """
    erDiagram
      CUSTOMER ||--o{ ORDER : places
      CUSTOMER {
        string name
        string custNumber
        string sector
      }
      ORDER ||--|{ LINE-ITEM : contains
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(ENTITY_RELATIONSHIP)
    >Element(ER_DOCUMENT)
    >>Element(ER_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(ER_LINE)
    >>>Element(IDENTIFIER)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>Element(RELATIONSHIP)
    >>>>PsiElement(ONLY_ONE)
    >>>>PsiElement(IDENTIFYING)
    >>>>PsiElement(ZERO_OR_MORE_RIGHT)
    >>>PsiWhiteSpace
    >>>Element(IDENTIFIER)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(COLON)
    >>>PsiWhiteSpace
    >>>PsiElement(LABEL)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(ER_LINE)
    >>>Element(IDENTIFIER)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(OPEN_CURLY)
    >>>Element(ER_ATTRIBUTE_LINE)
    >>>>PsiElement(EOL)
    >>>PsiWhiteSpace
    >>>Element(ER_ATTRIBUTE_LINE)
    >>>>Element(ER_ATTRIBUTE)
    >>>>>Element(ATTR_TYPE)
    >>>>>>PsiElement(ID)
    >>>>>PsiWhiteSpace
    >>>>>Element(ATTR_NAME)
    >>>>>>PsiElement(ID)
    >>>>PsiElement(EOL)
    >>>PsiWhiteSpace
    >>>Element(ER_ATTRIBUTE_LINE)
    >>>>Element(ER_ATTRIBUTE)
    >>>>>Element(ATTR_TYPE)
    >>>>>>PsiElement(ID)
    >>>>>PsiWhiteSpace
    >>>>>Element(ATTR_NAME)
    >>>>>>PsiElement(ID)
    >>>>PsiElement(EOL)
    >>>PsiWhiteSpace
    >>>Element(ER_ATTRIBUTE_LINE)
    >>>>Element(ER_ATTRIBUTE)
    >>>>>Element(ATTR_TYPE)
    >>>>>>PsiElement(ID)
    >>>>>PsiWhiteSpace
    >>>>>Element(ATTR_NAME)
    >>>>>>PsiElement(ID)
    >>>>PsiElement(EOL)
    >>>PsiWhiteSpace
    >>>PsiElement(CLOSE_CURLY)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(ER_LINE)
    >>>Element(IDENTIFIER)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>Element(RELATIONSHIP)
    >>>>PsiElement(ONLY_ONE)
    >>>>PsiElement(IDENTIFYING)
    >>>>PsiElement(ONE_OR_MORE_RIGHT)
    >>>PsiWhiteSpace
    >>>Element(IDENTIFIER)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(COLON)
    >>>PsiWhiteSpace
    >>>PsiElement(LABEL)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test entity with attribute keys and comments`() {
    val content = """
    erDiagram
      CAR ||--o{ NAMED-DRIVER : allows
      CAR {
        string allowedDriver FK "The license of the allowed driver"
        string registrationNumber PK
        string make "comment"
        string model
      }
      PERSON ||--o{ NAMED-DRIVER : is
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(ENTITY_RELATIONSHIP)
    >Element(ER_DOCUMENT)
    >>Element(ER_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(ER_LINE)
    >>>Element(IDENTIFIER)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>Element(RELATIONSHIP)
    >>>>PsiElement(ONLY_ONE)
    >>>>PsiElement(IDENTIFYING)
    >>>>PsiElement(ZERO_OR_MORE_RIGHT)
    >>>PsiWhiteSpace
    >>>Element(IDENTIFIER)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(COLON)
    >>>PsiWhiteSpace
    >>>PsiElement(LABEL)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(ER_LINE)
    >>>Element(IDENTIFIER)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(OPEN_CURLY)
    >>>Element(ER_ATTRIBUTE_LINE)
    >>>>PsiElement(EOL)
    >>>PsiWhiteSpace
    >>>Element(ER_ATTRIBUTE_LINE)
    >>>>Element(ER_ATTRIBUTE)
    >>>>>Element(ATTR_TYPE)
    >>>>>>PsiElement(ID)
    >>>>>PsiWhiteSpace
    >>>>>Element(ATTR_NAME)
    >>>>>>PsiElement(ID)
    >>>>>PsiWhiteSpace
    >>>>>PsiElement(ATTR_KEY)
    >>>>>PsiWhiteSpace
    >>>>>Element(STRING)
    >>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>>>PsiElement(STRING_VALUE)
    >>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>PsiElement(EOL)
    >>>PsiWhiteSpace
    >>>Element(ER_ATTRIBUTE_LINE)
    >>>>Element(ER_ATTRIBUTE)
    >>>>>Element(ATTR_TYPE)
    >>>>>>PsiElement(ID)
    >>>>>PsiWhiteSpace
    >>>>>Element(ATTR_NAME)
    >>>>>>PsiElement(ID)
    >>>>>PsiWhiteSpace
    >>>>>PsiElement(ATTR_KEY)
    >>>>PsiElement(EOL)
    >>>PsiWhiteSpace
    >>>Element(ER_ATTRIBUTE_LINE)
    >>>>Element(ER_ATTRIBUTE)
    >>>>>Element(ATTR_TYPE)
    >>>>>>PsiElement(ID)
    >>>>>PsiWhiteSpace
    >>>>>Element(ATTR_NAME)
    >>>>>>PsiElement(ID)
    >>>>>PsiWhiteSpace
    >>>>>Element(STRING)
    >>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>>>PsiElement(STRING_VALUE)
    >>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>PsiElement(EOL)
    >>>PsiWhiteSpace
    >>>Element(ER_ATTRIBUTE_LINE)
    >>>>Element(ER_ATTRIBUTE)
    >>>>>Element(ATTR_TYPE)
    >>>>>>PsiElement(ID)
    >>>>>PsiWhiteSpace
    >>>>>Element(ATTR_NAME)
    >>>>>>PsiElement(ID)
    >>>>PsiElement(EOL)
    >>>PsiWhiteSpace
    >>>PsiElement(CLOSE_CURLY)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(ER_LINE)
    >>>Element(IDENTIFIER)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>Element(RELATIONSHIP)
    >>>>PsiElement(ONLY_ONE)
    >>>>PsiElement(IDENTIFYING)
    >>>>PsiElement(ZERO_OR_MORE_RIGHT)
    >>>PsiWhiteSpace
    >>>Element(IDENTIFIER)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(COLON)
    >>>PsiWhiteSpace
    >>>PsiElement(LABEL)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test entity relationship with double quoted label`() {
    val content = """
    erDiagram
      CUSTOMER ||--o{ ORDER : "pla ce s"
      ORDER ||--|{ LINE-ITEM : ""
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(ENTITY_RELATIONSHIP)
    >Element(ER_DOCUMENT)
    >>Element(ER_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(ER_LINE)
    >>>Element(IDENTIFIER)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>Element(RELATIONSHIP)
    >>>>PsiElement(ONLY_ONE)
    >>>>PsiElement(IDENTIFYING)
    >>>>PsiElement(ZERO_OR_MORE_RIGHT)
    >>>PsiWhiteSpace
    >>>Element(IDENTIFIER)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(COLON)
    >>>PsiWhiteSpace
    >>>Element(STRING)
    >>>>PsiElement(DOUBLE_QUOTE)
    >>>>PsiElement(STRING_VALUE)
    >>>>PsiElement(DOUBLE_QUOTE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(ER_LINE)
    >>>Element(IDENTIFIER)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>Element(RELATIONSHIP)
    >>>>PsiElement(ONLY_ONE)
    >>>>PsiElement(IDENTIFYING)
    >>>>PsiElement(ONE_OR_MORE_RIGHT)
    >>>PsiWhiteSpace
    >>>Element(IDENTIFIER)
    >>>>PsiElement(ID)
    >>>PsiWhiteSpace
    >>>PsiElement(COLON)
    >>>PsiWhiteSpace
    >>>Element(STRING)
    >>>>PsiElement(DOUBLE_QUOTE)
    >>>>PsiElement(DOUBLE_QUOTE)
    """.trimIndent()
    doTest(content, expectedTree)
  }
}
