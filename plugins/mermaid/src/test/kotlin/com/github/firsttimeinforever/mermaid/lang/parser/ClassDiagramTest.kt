package com.github.firsttimeinforever.mermaid.lang.parser

class ClassDiagramTest : MermaidParserTestCase() {
  fun `test simple class definition`() {
    val content = """
    classDiagram
      class BankAccount
      BankAccount : +String owner
      BankAccount : +deposit(amount)
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(CLASS_DIAGRAM)
    >Element(CLASS_DOCUMENT)
    >>Element(CLASS_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(CLASS_STATEMENT)
    >>>>PsiElement(CLASS)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(MEMBER_STATEMENT)
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>PsiElement(COLON)
    >>>>PsiWhiteSpace
    >>>>Element(ATTR_OR_METHOD)
    >>>>>Element(VISIBILITY_AT_START)
    >>>>>>PsiElement(PLUS)
    >>>>>Element(ATTRIBUTE)
    >>>>>>Element(ATTR_TYPE)
    >>>>>>>PsiElement(ID)
    >>>>>>PsiWhiteSpace
    >>>>>>Element(ATTR_NAME)
    >>>>>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(MEMBER_STATEMENT)
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>PsiElement(COLON)
    >>>>PsiWhiteSpace
    >>>>Element(ATTR_OR_METHOD)
    >>>>>Element(VISIBILITY_AT_START)
    >>>>>>PsiElement(PLUS)
    >>>>>Element(METHOD)
    >>>>>>Element(ATTR_NAME)
    >>>>>>>PsiElement(ID)
    >>>>>>PsiElement(ROUND_START)
    >>>>>>Element(ATTRIBUTE)
    >>>>>>>Element(ATTR_NAME)
    >>>>>>>>PsiElement(ID)
    >>>>>>PsiElement(ROUND_END)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test class definition in brackets`() {
    val content = """
    classDiagram
      class BankAccount {
        +String owner
        +deposit(amount) bool
      }
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(CLASS_DIAGRAM)
    >Element(CLASS_DOCUMENT)
    >>Element(CLASS_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(CLASS_STATEMENT)
    >>>>PsiElement(CLASS)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>PsiElement(OPEN_CURLY)
    >>>>Element(MEMBER_LINE)
    >>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>Element(MEMBER_LINE)
    >>>>>Element(ATTR_OR_METHOD)
    >>>>>>Element(VISIBILITY_AT_START)
    >>>>>>>PsiElement(PLUS)
    >>>>>>Element(ATTRIBUTE)
    >>>>>>>Element(ATTR_TYPE)
    >>>>>>>>PsiElement(ID)
    >>>>>>>PsiWhiteSpace
    >>>>>>>Element(ATTR_NAME)
    >>>>>>>>PsiElement(ID)
    >>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>Element(MEMBER_LINE)
    >>>>>Element(ATTR_OR_METHOD)
    >>>>>>Element(VISIBILITY_AT_START)
    >>>>>>>PsiElement(PLUS)
    >>>>>>Element(METHOD)
    >>>>>>>Element(ATTR_NAME)
    >>>>>>>>PsiElement(ID)
    >>>>>>>PsiElement(ROUND_START)
    >>>>>>>Element(ATTRIBUTE)
    >>>>>>>>Element(ATTR_NAME)
    >>>>>>>>>PsiElement(ID)
    >>>>>>>PsiElement(ROUND_END)
    >>>>>>>PsiWhiteSpace
    >>>>>>>Element(ATTR_TYPE)
    >>>>>>>>PsiElement(ID)
    >>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>PsiElement(CLOSE_CURLY)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test class with generics`() {
    val content = """
    classDiagram
      class Square~Shape~{
        int id
        List~int~ position
        setPoints(List~int~ points)
        getPoints() List~int~
      }
      Square : -List~string~ messages
      Square : +setMessages(List~string~ messages)
      Square : +getMessages() List~string~
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(CLASS_DIAGRAM)
    >Element(CLASS_DOCUMENT)
    >>Element(CLASS_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(CLASS_STATEMENT)
    >>>>PsiElement(CLASS)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    >>>>PsiElement(TILDA)
    >>>>PsiElement(GENERIC_TYPE)
    >>>>PsiElement(TILDA)
    >>>>PsiElement(OPEN_CURLY)
    >>>>Element(MEMBER_LINE)
    >>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>Element(MEMBER_LINE)
    >>>>>Element(ATTR_OR_METHOD)
    >>>>>>Element(ATTRIBUTE)
    >>>>>>>Element(ATTR_TYPE)
    >>>>>>>>PsiElement(ID)
    >>>>>>>PsiWhiteSpace
    >>>>>>>Element(ATTR_NAME)
    >>>>>>>>PsiElement(ID)
    >>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>Element(MEMBER_LINE)
    >>>>>Element(ATTR_OR_METHOD)
    >>>>>>Element(ATTRIBUTE)
    >>>>>>>Element(ATTR_TYPE)
    >>>>>>>>PsiElement(ID)
    >>>>>>>>PsiElement(TILDA)
    >>>>>>>>PsiElement(GENERIC_TYPE)
    >>>>>>>>PsiElement(TILDA)
    >>>>>>>PsiWhiteSpace
    >>>>>>>Element(ATTR_NAME)
    >>>>>>>>PsiElement(ID)
    >>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>Element(MEMBER_LINE)
    >>>>>Element(ATTR_OR_METHOD)
    >>>>>>Element(METHOD)
    >>>>>>>Element(ATTR_NAME)
    >>>>>>>>PsiElement(ID)
    >>>>>>>PsiElement(ROUND_START)
    >>>>>>>Element(ATTRIBUTE)
    >>>>>>>>Element(ATTR_TYPE)
    >>>>>>>>>PsiElement(ID)
    >>>>>>>>>PsiElement(TILDA)
    >>>>>>>>>PsiElement(GENERIC_TYPE)
    >>>>>>>>>PsiElement(TILDA)
    >>>>>>>>PsiWhiteSpace
    >>>>>>>>Element(ATTR_NAME)
    >>>>>>>>>PsiElement(ID)
    >>>>>>>PsiElement(ROUND_END)
    >>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>Element(MEMBER_LINE)
    >>>>>Element(ATTR_OR_METHOD)
    >>>>>>Element(METHOD)
    >>>>>>>Element(ATTR_NAME)
    >>>>>>>>PsiElement(ID)
    >>>>>>>PsiElement(ROUND_START)
    >>>>>>>PsiElement(ROUND_END)
    >>>>>>>PsiWhiteSpace
    >>>>>>>Element(ATTR_TYPE)
    >>>>>>>>PsiElement(ID)
    >>>>>>>>PsiElement(TILDA)
    >>>>>>>>PsiElement(GENERIC_TYPE)
    >>>>>>>>PsiElement(TILDA)
    >>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>PsiElement(CLOSE_CURLY)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(MEMBER_STATEMENT)
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>PsiElement(COLON)
    >>>>PsiWhiteSpace
    >>>>Element(ATTR_OR_METHOD)
    >>>>>Element(VISIBILITY_AT_START)
    >>>>>>PsiElement(MINUS)
    >>>>>Element(ATTRIBUTE)
    >>>>>>Element(ATTR_TYPE)
    >>>>>>>PsiElement(ID)
    >>>>>>>PsiElement(TILDA)
    >>>>>>>PsiElement(GENERIC_TYPE)
    >>>>>>>PsiElement(TILDA)
    >>>>>>PsiWhiteSpace
    >>>>>>Element(ATTR_NAME)
    >>>>>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(MEMBER_STATEMENT)
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>PsiElement(COLON)
    >>>>PsiWhiteSpace
    >>>>Element(ATTR_OR_METHOD)
    >>>>>Element(VISIBILITY_AT_START)
    >>>>>>PsiElement(PLUS)
    >>>>>Element(METHOD)
    >>>>>>Element(ATTR_NAME)
    >>>>>>>PsiElement(ID)
    >>>>>>PsiElement(ROUND_START)
    >>>>>>Element(ATTRIBUTE)
    >>>>>>>Element(ATTR_TYPE)
    >>>>>>>>PsiElement(ID)
    >>>>>>>>PsiElement(TILDA)
    >>>>>>>>PsiElement(GENERIC_TYPE)
    >>>>>>>>PsiElement(TILDA)
    >>>>>>>PsiWhiteSpace
    >>>>>>>Element(ATTR_NAME)
    >>>>>>>>PsiElement(ID)
    >>>>>>PsiElement(ROUND_END)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(MEMBER_STATEMENT)
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>PsiElement(COLON)
    >>>>PsiWhiteSpace
    >>>>Element(ATTR_OR_METHOD)
    >>>>>Element(VISIBILITY_AT_START)
    >>>>>>PsiElement(PLUS)
    >>>>>Element(METHOD)
    >>>>>>Element(ATTR_NAME)
    >>>>>>>PsiElement(ID)
    >>>>>>PsiElement(ROUND_START)
    >>>>>>PsiElement(ROUND_END)
    >>>>>>PsiWhiteSpace
    >>>>>>Element(ATTR_TYPE)
    >>>>>>>PsiElement(ID)
    >>>>>>>PsiElement(TILDA)
    >>>>>>>PsiElement(GENERIC_TYPE)
    >>>>>>>PsiElement(TILDA)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test identifiers at end of member`() {
    val content = """
    classDiagram
      class BankAccount
      BankAccount : +someAbstractMethod()*
      BankAccount : +someStaticMethod()$
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(CLASS_DIAGRAM)
    >Element(CLASS_DOCUMENT)
    >>Element(CLASS_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(CLASS_STATEMENT)
    >>>>PsiElement(CLASS)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(MEMBER_STATEMENT)
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>PsiElement(COLON)
    >>>>PsiWhiteSpace
    >>>>Element(ATTR_OR_METHOD)
    >>>>>Element(VISIBILITY_AT_START)
    >>>>>>PsiElement(PLUS)
    >>>>>Element(METHOD)
    >>>>>>Element(ATTR_NAME)
    >>>>>>>PsiElement(ID)
    >>>>>>PsiElement(ROUND_START)
    >>>>>>PsiElement(ROUND_END)
    >>>>>>Element(VISIBILITY_AT_END)
    >>>>>>>PsiElement(STAR)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(MEMBER_STATEMENT)
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>PsiElement(COLON)
    >>>>PsiWhiteSpace
    >>>>Element(ATTR_OR_METHOD)
    >>>>>Element(VISIBILITY_AT_START)
    >>>>>>PsiElement(PLUS)
    >>>>>Element(METHOD)
    >>>>>>Element(ATTR_NAME)
    >>>>>>>PsiElement(ID)
    >>>>>>PsiElement(ROUND_START)
    >>>>>>PsiElement(ROUND_END)
    >>>>>>Element(VISIBILITY_AT_END)
    >>>>>>>PsiElement(DOLLAR)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test class relationships RL`() {
    val content = """
    classDiagram
      classA <|-- classB
      classC *-- classD
      classE o-- classF
      classG <-- classH
      classI -- classJ
      classK <.. classL
      classM <|.. classN
      classO .. classP
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(CLASS_DIAGRAM)
    >Element(CLASS_DOCUMENT)
    >>Element(CLASS_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(RELATION_STATEMENT)
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>Element(RELATION)
    >>>>>Element(RELATION_TYPE_LEFT)
    >>>>>>PsiElement(EXTENSION_START)
    >>>>>Element(LINE_TYPE)
    >>>>>>PsiElement(LINE)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(RELATION_STATEMENT)
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>Element(RELATION)
    >>>>>Element(RELATION_TYPE_LEFT)
    >>>>>>PsiElement(COMPOSITION)
    >>>>>Element(LINE_TYPE)
    >>>>>>PsiElement(LINE)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(RELATION_STATEMENT)
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>Element(RELATION)
    >>>>>Element(RELATION_TYPE_LEFT)
    >>>>>>PsiElement(AGGREGATION)
    >>>>>Element(LINE_TYPE)
    >>>>>>PsiElement(LINE)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(RELATION_STATEMENT)
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>Element(RELATION)
    >>>>>Element(RELATION_TYPE_LEFT)
    >>>>>>PsiElement(DEPENDENCY_START)
    >>>>>Element(LINE_TYPE)
    >>>>>>PsiElement(LINE)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(RELATION_STATEMENT)
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>Element(RELATION)
    >>>>>Element(LINE_TYPE)
    >>>>>>PsiElement(LINE)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(RELATION_STATEMENT)
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>Element(RELATION)
    >>>>>Element(RELATION_TYPE_LEFT)
    >>>>>>PsiElement(DEPENDENCY_START)
    >>>>>Element(LINE_TYPE)
    >>>>>>PsiElement(DOTTED_LINE)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(RELATION_STATEMENT)
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>Element(RELATION)
    >>>>>Element(RELATION_TYPE_LEFT)
    >>>>>>PsiElement(EXTENSION_START)
    >>>>>Element(LINE_TYPE)
    >>>>>>PsiElement(DOTTED_LINE)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(RELATION_STATEMENT)
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>Element(RELATION)
    >>>>>Element(LINE_TYPE)
    >>>>>>PsiElement(DOTTED_LINE)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test class relationships LR`() {
    val content = """
    classDiagram
      classA --|> classB
      classC --* classD
      classE --o classF
      classG --> classH
      classI -- classJ
      classK ..> classL
      classM ..|> classN
      classO .. classP
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(CLASS_DIAGRAM)
    >Element(CLASS_DOCUMENT)
    >>Element(CLASS_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(RELATION_STATEMENT)
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>Element(RELATION)
    >>>>>Element(LINE_TYPE)
    >>>>>>PsiElement(LINE)
    >>>>>Element(RELATION_TYPE_RIGHT)
    >>>>>>PsiElement(EXTENSION_END)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(RELATION_STATEMENT)
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>Element(RELATION)
    >>>>>Element(LINE_TYPE)
    >>>>>>PsiElement(LINE)
    >>>>>Element(RELATION_TYPE_RIGHT)
    >>>>>>PsiElement(COMPOSITION)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(RELATION_STATEMENT)
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>Element(RELATION)
    >>>>>Element(LINE_TYPE)
    >>>>>>PsiElement(LINE)
    >>>>>Element(RELATION_TYPE_RIGHT)
    >>>>>>PsiElement(AGGREGATION)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(RELATION_STATEMENT)
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>Element(RELATION)
    >>>>>Element(LINE_TYPE)
    >>>>>>PsiElement(LINE)
    >>>>>Element(RELATION_TYPE_RIGHT)
    >>>>>>PsiElement(DEPENDENCY_END)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(RELATION_STATEMENT)
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>Element(RELATION)
    >>>>>Element(LINE_TYPE)
    >>>>>>PsiElement(LINE)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(RELATION_STATEMENT)
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>Element(RELATION)
    >>>>>Element(LINE_TYPE)
    >>>>>>PsiElement(DOTTED_LINE)
    >>>>>Element(RELATION_TYPE_RIGHT)
    >>>>>>PsiElement(DEPENDENCY_END)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(RELATION_STATEMENT)
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>Element(RELATION)
    >>>>>Element(LINE_TYPE)
    >>>>>>PsiElement(DOTTED_LINE)
    >>>>>Element(RELATION_TYPE_RIGHT)
    >>>>>>PsiElement(EXTENSION_END)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(RELATION_STATEMENT)
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>Element(RELATION)
    >>>>>Element(LINE_TYPE)
    >>>>>>PsiElement(DOTTED_LINE)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test class two-way relationship`() {
    val content = """
    classDiagram
      Animal <|--|> Zebra
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(CLASS_DIAGRAM)
    >Element(CLASS_DOCUMENT)
    >>Element(CLASS_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(RELATION_STATEMENT)
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>Element(RELATION)
    >>>>>Element(RELATION_TYPE_LEFT)
    >>>>>>PsiElement(EXTENSION_START)
    >>>>>Element(LINE_TYPE)
    >>>>>>PsiElement(LINE)
    >>>>>Element(RELATION_TYPE_RIGHT)
    >>>>>>PsiElement(EXTENSION_END)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test class relationship with label`() {
    val content = """
    classDiagram
      classA <|-- classB : implements
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(CLASS_DIAGRAM)
    >Element(CLASS_DOCUMENT)
    >>Element(CLASS_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(RELATION_STATEMENT)
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>Element(RELATION)
    >>>>>Element(RELATION_TYPE_LEFT)
    >>>>>>PsiElement(EXTENSION_START)
    >>>>>Element(LINE_TYPE)
    >>>>>>PsiElement(LINE)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>PsiElement(COLON)
    >>>>PsiElement(LABEL)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test class relationship with cardinality`() {
    val content = """
    classDiagram
      Customer "1" --> "*" Ticket
      Student "1" --> "1..*" Course
      Galaxy --> "many" Star : Contains
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(CLASS_DIAGRAM)
    >Element(CLASS_DOCUMENT)
    >>Element(CLASS_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(RELATION_STATEMENT)
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>Element(RELATION)
    >>>>>Element(CARDINALITY)
    >>>>>>Element(STRING)
    >>>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>>>>PsiElement(STRING_VALUE)
    >>>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>>PsiWhiteSpace
    >>>>>Element(LINE_TYPE)
    >>>>>>PsiElement(LINE)
    >>>>>Element(RELATION_TYPE_RIGHT)
    >>>>>>PsiElement(DEPENDENCY_END)
    >>>>>PsiWhiteSpace
    >>>>>Element(CARDINALITY)
    >>>>>>Element(STRING)
    >>>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>>>>PsiElement(STRING_VALUE)
    >>>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(RELATION_STATEMENT)
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>Element(RELATION)
    >>>>>Element(CARDINALITY)
    >>>>>>Element(STRING)
    >>>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>>>>PsiElement(STRING_VALUE)
    >>>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>>PsiWhiteSpace
    >>>>>Element(LINE_TYPE)
    >>>>>>PsiElement(LINE)
    >>>>>Element(RELATION_TYPE_RIGHT)
    >>>>>>PsiElement(DEPENDENCY_END)
    >>>>>PsiWhiteSpace
    >>>>>Element(CARDINALITY)
    >>>>>>Element(STRING)
    >>>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>>>>PsiElement(STRING_VALUE)
    >>>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(RELATION_STATEMENT)
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>Element(RELATION)
    >>>>>Element(LINE_TYPE)
    >>>>>>PsiElement(LINE)
    >>>>>Element(RELATION_TYPE_RIGHT)
    >>>>>>PsiElement(DEPENDENCY_END)
    >>>>>PsiWhiteSpace
    >>>>>Element(CARDINALITY)
    >>>>>>Element(STRING)
    >>>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>>>>PsiElement(STRING_VALUE)
    >>>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>PsiElement(COLON)
    >>>>PsiElement(LABEL)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test class with annotation`() {
    val content = """
    classDiagram
      class Shape
      <<interface>> Shape
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(CLASS_DIAGRAM)
    >Element(CLASS_DOCUMENT)
    >>Element(CLASS_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(CLASS_STATEMENT)
    >>>>PsiElement(CLASS)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(ANNOTATION_STATEMENT)
    >>>>Element(ANNOTATION)
    >>>>>PsiElement(ANNOTATION_START)
    >>>>>PsiElement(ANNOTATION_VALUE)
    >>>>>PsiElement(ANNOTATION_END)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test class with annotation in struct`() {
    val content = """
    classDiagram
      class Color {
        <<enumeration>>
        RED
        BLUE
      }
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(CLASS_DIAGRAM)
    >Element(CLASS_DOCUMENT)
    >>Element(CLASS_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(CLASS_STATEMENT)
    >>>>PsiElement(CLASS)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>PsiElement(OPEN_CURLY)
    >>>>Element(MEMBER_LINE)
    >>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>Element(MEMBER_LINE)
    >>>>>Element(ANNOTATION)
    >>>>>>PsiElement(ANNOTATION_START)
    >>>>>>PsiElement(ANNOTATION_VALUE)
    >>>>>>PsiElement(ANNOTATION_END)
    >>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>Element(MEMBER_LINE)
    >>>>>Element(ATTR_OR_METHOD)
    >>>>>>Element(ATTRIBUTE)
    >>>>>>>Element(ATTR_NAME)
    >>>>>>>>PsiElement(ID)
    >>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>Element(MEMBER_LINE)
    >>>>>Element(ATTR_OR_METHOD)
    >>>>>>Element(ATTRIBUTE)
    >>>>>>>Element(ATTR_NAME)
    >>>>>>>>PsiElement(ID)
    >>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>PsiElement(CLOSE_CURLY)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test class with direction`() {
    val content = """
    classDiagram
      direction RL
      class Student {
        -idCard : IdCard
      }
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(CLASS_DIAGRAM)
    >Element(CLASS_DOCUMENT)
    >>Element(CLASS_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>PsiElement(DIRECTION)
    >>>PsiWhiteSpace
    >>>PsiElement(DIR)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(CLASS_STATEMENT)
    >>>>PsiElement(CLASS)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>PsiElement(OPEN_CURLY)
    >>>>Element(MEMBER_LINE)
    >>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>Element(MEMBER_LINE)
    >>>>>Element(ATTR_OR_METHOD)
    >>>>>>Element(VISIBILITY_AT_START)
    >>>>>>>PsiElement(MINUS)
    >>>>>>Element(ATTRIBUTE)
    >>>>>>>Element(ATTR_NAME)
    >>>>>>>>PsiElement(ID)
    >>>>>>>PsiWhiteSpace
    >>>>>>>PsiElement(COLON)
    >>>>>>>PsiWhiteSpace
    >>>>>>>Element(ATTR_TYPE)
    >>>>>>>>PsiElement(ID)
    >>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>PsiElement(CLOSE_CURLY)
    """.trimIndent()
    doTest(content, expectedTree)
  }

  fun `test class with style`() {
    val content = """
    classDiagram
      class Animal:::cssClass {
        -int sizeInFeet
        -canEat()
      }
    """.trimIndent()
    val expectedTree = """
    Element(FILE)
    >PsiElement(CLASS_DIAGRAM)
    >Element(CLASS_DOCUMENT)
    >>Element(CLASS_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(CLASS_LINE)
    >>>Element(CLASS_STATEMENT)
    >>>>PsiElement(CLASS)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ID)
    >>>>PsiElement(STYLE_SEPARATOR)
    >>>>PsiElement(ID)
    >>>>PsiWhiteSpace
    >>>>PsiElement(OPEN_CURLY)
    >>>>Element(MEMBER_LINE)
    >>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>Element(MEMBER_LINE)
    >>>>>Element(ATTR_OR_METHOD)
    >>>>>>Element(VISIBILITY_AT_START)
    >>>>>>>PsiElement(MINUS)
    >>>>>>Element(ATTRIBUTE)
    >>>>>>>Element(ATTR_TYPE)
    >>>>>>>>PsiElement(ID)
    >>>>>>>PsiWhiteSpace
    >>>>>>>Element(ATTR_NAME)
    >>>>>>>>PsiElement(ID)
    >>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>Element(MEMBER_LINE)
    >>>>>Element(ATTR_OR_METHOD)
    >>>>>>Element(VISIBILITY_AT_START)
    >>>>>>>PsiElement(MINUS)
    >>>>>>Element(METHOD)
    >>>>>>>Element(ATTR_NAME)
    >>>>>>>>PsiElement(ID)
    >>>>>>>PsiElement(ROUND_START)
    >>>>>>>PsiElement(ROUND_END)
    >>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>PsiElement(CLOSE_CURLY)
    """.trimIndent()
    doTest(content, expectedTree)
  }
}
