package com.github.firsttimeinforever.mermaid.lang.lexer

import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ANNOTATION_END
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ANNOTATION_START
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ANNOTATION_VALUE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.CLASS
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.CLOSE_CURLY
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.CLOSE_ROUND
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COLON
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ClassDiagram.AGGREGATION
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ClassDiagram.CLASS_DIAGRAM
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ClassDiagram.COMPOSITION
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ClassDiagram.DEPENDENCY_END
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ClassDiagram.DEPENDENCY_START
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ClassDiagram.DOTTED_LINE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ClassDiagram.EXTENSION_END
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ClassDiagram.EXTENSION_START
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ClassDiagram.GENERIC_TYPE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ClassDiagram.LINE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.DIR
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.DIRECTION
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.DOLLAR
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.DOUBLE_QUOTE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.EOL
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ID
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.LABEL
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.MINUS
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.OPEN_CURLY
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.OPEN_ROUND
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.PLUS
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.STAR
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.STRING_VALUE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.STYLE_SEPARATOR
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.TILDA
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.WHITE_SPACE

class ClassDiagramTest : MermaidLexerTestCase() {
  fun `test simple class definition`() {
    val content = """
    classDiagram
      class BankAccount
      BankAccount : +String owner
      BankAccount : +deposit(amount)
    """.trimIndent()
    val expected = listOf(
      Token(CLASS_DIAGRAM, 0, 12, "classDiagram"),
      Token(EOL, 12, 13, "\n"),
      Token(WHITE_SPACE, 13, 15, "  "),
      Token(CLASS, 15, 20, "class"),
      Token(WHITE_SPACE, 20, 21, " "),
      Token(ID, 21, 32, "BankAccount"),
      Token(EOL, 32, 33, "\n"),
      Token(WHITE_SPACE, 33, 35, "  "),
      Token(ID, 35, 46, "BankAccount"),
      Token(WHITE_SPACE, 46, 47, " "),
      Token(COLON, 47, 48, ":"),
      Token(WHITE_SPACE, 48, 49, " "),
      Token(PLUS, 49, 50, "+"),
      Token(ID, 50, 56, "String"),
      Token(WHITE_SPACE, 56, 57, " "),
      Token(ID, 57, 62, "owner"),
      Token(EOL, 62, 63, "\n"),
      Token(WHITE_SPACE, 63, 65, "  "),
      Token(ID, 65, 76, "BankAccount"),
      Token(WHITE_SPACE, 76, 77, " "),
      Token(COLON, 77, 78, ":"),
      Token(WHITE_SPACE, 78, 79, " "),
      Token(PLUS, 79, 80, "+"),
      Token(ID, 80, 87, "deposit"),
      Token(OPEN_ROUND, 87, 88, "("),
      Token(ID, 88, 94, "amount"),
      Token(CLOSE_ROUND, 94, 95, ")")
    )
    doTest(content, expected)
  }

  fun `test class definition in brackets`() {
    val content = """
    classDiagram
      class BankAccount {
        +String owner
        +deposit(amount) bool
      }
    """.trimIndent()
    val expected = listOf(
      Token(CLASS_DIAGRAM, 0, 12, "classDiagram"),
      Token(EOL, 12, 13, "\n"),
      Token(WHITE_SPACE, 13, 15, "  "),
      Token(CLASS, 15, 20, "class"),
      Token(WHITE_SPACE, 20, 21, " "),
      Token(ID, 21, 32, "BankAccount"),
      Token(WHITE_SPACE, 32, 33, " "),
      Token(OPEN_CURLY, 33, 34, "{"),
      Token(EOL, 34, 35, "\n"),
      Token(WHITE_SPACE, 35, 39, "    "),
      Token(PLUS, 39, 40, "+"),
      Token(ID, 40, 46, "String"),
      Token(WHITE_SPACE, 46, 47, " "),
      Token(ID, 47, 52, "owner"),
      Token(EOL, 52, 53, "\n"),
      Token(WHITE_SPACE, 53, 57, "    "),
      Token(PLUS, 57, 58, "+"),
      Token(ID, 58, 65, "deposit"),
      Token(OPEN_ROUND, 65, 66, "("),
      Token(ID, 66, 72, "amount"),
      Token(CLOSE_ROUND, 72, 73, ")"),
      Token(WHITE_SPACE, 73, 74, " "),
      Token(ID, 74, 78, "bool"),
      Token(EOL, 78, 79, "\n"),
      Token(WHITE_SPACE, 79, 81, "  "),
      Token(CLOSE_CURLY, 81, 82, "}")
    )
    doTest(content, expected)
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
    val expected = listOf(
      Token(CLASS_DIAGRAM, 0, 12, "classDiagram"),
      Token(EOL, 12, 13, "\n"),
      Token(WHITE_SPACE, 13, 15, "  "),
      Token(CLASS, 15, 20, "class"),
      Token(WHITE_SPACE, 20, 21, " "),
      Token(ID, 21, 27, "Square"),
      Token(TILDA, 27, 28, "~"),
      Token(GENERIC_TYPE, 28, 33, "Shape"),
      Token(TILDA, 33, 34, "~"),
      Token(OPEN_CURLY, 34, 35, "{"),
      Token(EOL, 35, 36, "\n"),
      Token(WHITE_SPACE, 36, 40, "    "),
      Token(ID, 40, 43, "int"),
      Token(WHITE_SPACE, 43, 44, " "),
      Token(ID, 44, 46, "id"),
      Token(EOL, 46, 47, "\n"),
      Token(WHITE_SPACE, 47, 51, "    "),
      Token(ID, 51, 55, "List"),
      Token(TILDA, 55, 56, "~"),
      Token(GENERIC_TYPE, 56, 59, "int"),
      Token(TILDA, 59, 60, "~"),
      Token(WHITE_SPACE, 60, 61, " "),
      Token(ID, 61, 69, "position"),
      Token(EOL, 69, 70, "\n"),
      Token(WHITE_SPACE, 70, 74, "    "),
      Token(ID, 74, 83, "setPoints"),
      Token(OPEN_ROUND, 83, 84, "("),
      Token(ID, 84, 88, "List"),
      Token(TILDA, 88, 89, "~"),
      Token(GENERIC_TYPE, 89, 92, "int"),
      Token(TILDA, 92, 93, "~"),
      Token(WHITE_SPACE, 93, 94, " "),
      Token(ID, 94, 100, "points"),
      Token(CLOSE_ROUND, 100, 101, ")"),
      Token(EOL, 101, 102, "\n"),
      Token(WHITE_SPACE, 102, 106, "    "),
      Token(ID, 106, 115, "getPoints"),
      Token(OPEN_ROUND, 115, 116, "("),
      Token(CLOSE_ROUND, 116, 117, ")"),
      Token(WHITE_SPACE, 117, 118, " "),
      Token(ID, 118, 122, "List"),
      Token(TILDA, 122, 123, "~"),
      Token(GENERIC_TYPE, 123, 126, "int"),
      Token(TILDA, 126, 127, "~"),
      Token(EOL, 127, 128, "\n"),
      Token(WHITE_SPACE, 128, 130, "  "),
      Token(CLOSE_CURLY, 130, 131, "}"),
      Token(EOL, 131, 132, "\n"),
      Token(WHITE_SPACE, 132, 134, "  "),
      Token(ID, 134, 140, "Square"),
      Token(WHITE_SPACE, 140, 141, " "),
      Token(COLON, 141, 142, ":"),
      Token(WHITE_SPACE, 142, 143, " "),
      Token(MINUS, 143, 144, "-"),
      Token(ID, 144, 148, "List"),
      Token(TILDA, 148, 149, "~"),
      Token(GENERIC_TYPE, 149, 155, "string"),
      Token(TILDA, 155, 156, "~"),
      Token(WHITE_SPACE, 156, 157, " "),
      Token(ID, 157, 165, "messages"),
      Token(EOL, 165, 166, "\n"),
      Token(WHITE_SPACE, 166, 168, "  "),
      Token(ID, 168, 174, "Square"),
      Token(WHITE_SPACE, 174, 175, " "),
      Token(COLON, 175, 176, ":"),
      Token(WHITE_SPACE, 176, 177, " "),
      Token(PLUS, 177, 178, "+"),
      Token(ID, 178, 189, "setMessages"),
      Token(OPEN_ROUND, 189, 190, "("),
      Token(ID, 190, 194, "List"),
      Token(TILDA, 194, 195, "~"),
      Token(GENERIC_TYPE, 195, 201, "string"),
      Token(TILDA, 201, 202, "~"),
      Token(WHITE_SPACE, 202, 203, " "),
      Token(ID, 203, 211, "messages"),
      Token(CLOSE_ROUND, 211, 212, ")"),
      Token(EOL, 212, 213, "\n"),
      Token(WHITE_SPACE, 213, 215, "  "),
      Token(ID, 215, 221, "Square"),
      Token(WHITE_SPACE, 221, 222, " "),
      Token(COLON, 222, 223, ":"),
      Token(WHITE_SPACE, 223, 224, " "),
      Token(PLUS, 224, 225, "+"),
      Token(ID, 225, 236, "getMessages"),
      Token(OPEN_ROUND, 236, 237, "("),
      Token(CLOSE_ROUND, 237, 238, ")"),
      Token(WHITE_SPACE, 238, 239, " "),
      Token(ID, 239, 243, "List"),
      Token(TILDA, 243, 244, "~"),
      Token(GENERIC_TYPE, 244, 250, "string"),
      Token(TILDA, 250, 251, "~")
    )
    doTest(content, expected)
  }

  fun `test identifiers at end of member`() {
    val content = """
    classDiagram
      class BankAccount
      BankAccount : +someAbstractMethod()*
      BankAccount : +someStaticMethod()$
    """.trimIndent()
    val expected = listOf(
      Token(CLASS_DIAGRAM, 0, 12, "classDiagram"),
      Token(EOL, 12, 13, "\n"),
      Token(WHITE_SPACE, 13, 15, "  "),
      Token(CLASS, 15, 20, "class"),
      Token(WHITE_SPACE, 20, 21, " "),
      Token(ID, 21, 32, "BankAccount"),
      Token(EOL, 32, 33, "\n"),
      Token(WHITE_SPACE, 33, 35, "  "),
      Token(ID, 35, 46, "BankAccount"),
      Token(WHITE_SPACE, 46, 47, " "),
      Token(COLON, 47, 48, ":"),
      Token(WHITE_SPACE, 48, 49, " "),
      Token(PLUS, 49, 50, "+"),
      Token(ID, 50, 68, "someAbstractMethod"),
      Token(OPEN_ROUND, 68, 69, "("),
      Token(CLOSE_ROUND, 69, 70, ")"),
      Token(STAR, 70, 71, "*"),
      Token(EOL, 71, 72, "\n"),
      Token(WHITE_SPACE, 72, 74, "  "),
      Token(ID, 74, 85, "BankAccount"),
      Token(WHITE_SPACE, 85, 86, " "),
      Token(COLON, 86, 87, ":"),
      Token(WHITE_SPACE, 87, 88, " "),
      Token(PLUS, 88, 89, "+"),
      Token(ID, 89, 105, "someStaticMethod"),
      Token(OPEN_ROUND, 105, 106, "("),
      Token(CLOSE_ROUND, 106, 107, ")"),
      Token(DOLLAR, 107, 108, "$")
    )
    doTest(content, expected)
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
    val expected = listOf(
      Token(CLASS_DIAGRAM, 0, 12, "classDiagram"),
      Token(EOL, 12, 13, "\n"),
      Token(WHITE_SPACE, 13, 15, "  "),
      Token(ID, 15, 21, "classA"),
      Token(WHITE_SPACE, 21, 22, " "),
      Token(EXTENSION_START, 22, 24, "<|"),
      Token(LINE, 24, 26, "--"),
      Token(WHITE_SPACE, 26, 27, " "),
      Token(ID, 27, 33, "classB"),
      Token(EOL, 33, 34, "\n"),
      Token(WHITE_SPACE, 34, 36, "  "),
      Token(ID, 36, 42, "classC"),
      Token(WHITE_SPACE, 42, 43, " "),
      Token(COMPOSITION, 43, 44, "*"),
      Token(LINE, 44, 46, "--"),
      Token(WHITE_SPACE, 46, 47, " "),
      Token(ID, 47, 53, "classD"),
      Token(EOL, 53, 54, "\n"),
      Token(WHITE_SPACE, 54, 56, "  "),
      Token(ID, 56, 62, "classE"),
      Token(WHITE_SPACE, 62, 63, " "),
      Token(AGGREGATION, 63, 64, "o"),
      Token(LINE, 64, 66, "--"),
      Token(WHITE_SPACE, 66, 67, " "),
      Token(ID, 67, 73, "classF"),
      Token(EOL, 73, 74, "\n"),
      Token(WHITE_SPACE, 74, 76, "  "),
      Token(ID, 76, 82, "classG"),
      Token(WHITE_SPACE, 82, 83, " "),
      Token(DEPENDENCY_START, 83, 84, "<"),
      Token(LINE, 84, 86, "--"),
      Token(WHITE_SPACE, 86, 87, " "),
      Token(ID, 87, 93, "classH"),
      Token(EOL, 93, 94, "\n"),
      Token(WHITE_SPACE, 94, 96, "  "),
      Token(ID, 96, 102, "classI"),
      Token(WHITE_SPACE, 102, 103, " "),
      Token(LINE, 103, 105, "--"),
      Token(WHITE_SPACE, 105, 106, " "),
      Token(ID, 106, 112, "classJ"),
      Token(EOL, 112, 113, "\n"),
      Token(WHITE_SPACE, 113, 115, "  "),
      Token(ID, 115, 121, "classK"),
      Token(WHITE_SPACE, 121, 122, " "),
      Token(DEPENDENCY_START, 122, 123, "<"),
      Token(DOTTED_LINE, 123, 125, ".."),
      Token(WHITE_SPACE, 125, 126, " "),
      Token(ID, 126, 132, "classL"),
      Token(EOL, 132, 133, "\n"),
      Token(WHITE_SPACE, 133, 135, "  "),
      Token(ID, 135, 141, "classM"),
      Token(WHITE_SPACE, 141, 142, " "),
      Token(EXTENSION_START, 142, 144, "<|"),
      Token(DOTTED_LINE, 144, 146, ".."),
      Token(WHITE_SPACE, 146, 147, " "),
      Token(ID, 147, 153, "classN"),
      Token(EOL, 153, 154, "\n"),
      Token(WHITE_SPACE, 154, 156, "  "),
      Token(ID, 156, 162, "classO"),
      Token(WHITE_SPACE, 162, 163, " "),
      Token(DOTTED_LINE, 163, 165, ".."),
      Token(WHITE_SPACE, 165, 166, " "),
      Token(ID, 166, 172, "classP")
    )
    doTest(content, expected)
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
    val expected = listOf(
      Token(CLASS_DIAGRAM, 0, 12, "classDiagram"),
      Token(EOL, 12, 13, "\n"),
      Token(WHITE_SPACE, 13, 15, "  "),
      Token(ID, 15, 21, "classA"),
      Token(WHITE_SPACE, 21, 22, " "),
      Token(LINE, 22, 24, "--"),
      Token(EXTENSION_END, 24, 26, "|>"),
      Token(WHITE_SPACE, 26, 27, " "),
      Token(ID, 27, 33, "classB"),
      Token(EOL, 33, 34, "\n"),
      Token(WHITE_SPACE, 34, 36, "  "),
      Token(ID, 36, 42, "classC"),
      Token(WHITE_SPACE, 42, 43, " "),
      Token(LINE, 43, 45, "--"),
      Token(COMPOSITION, 45, 46, "*"),
      Token(WHITE_SPACE, 46, 47, " "),
      Token(ID, 47, 53, "classD"),
      Token(EOL, 53, 54, "\n"),
      Token(WHITE_SPACE, 54, 56, "  "),
      Token(ID, 56, 62, "classE"),
      Token(WHITE_SPACE, 62, 63, " "),
      Token(LINE, 63, 65, "--"),
      Token(AGGREGATION, 65, 66, "o"),
      Token(WHITE_SPACE, 66, 67, " "),
      Token(ID, 67, 73, "classF"),
      Token(EOL, 73, 74, "\n"),
      Token(WHITE_SPACE, 74, 76, "  "),
      Token(ID, 76, 82, "classG"),
      Token(WHITE_SPACE, 82, 83, " "),
      Token(LINE, 83, 85, "--"),
      Token(DEPENDENCY_END, 85, 86, ">"),
      Token(WHITE_SPACE, 86, 87, " "),
      Token(ID, 87, 93, "classH"),
      Token(EOL, 93, 94, "\n"),
      Token(WHITE_SPACE, 94, 96, "  "),
      Token(ID, 96, 102, "classI"),
      Token(WHITE_SPACE, 102, 103, " "),
      Token(LINE, 103, 105, "--"),
      Token(WHITE_SPACE, 105, 106, " "),
      Token(ID, 106, 112, "classJ"),
      Token(EOL, 112, 113, "\n"),
      Token(WHITE_SPACE, 113, 115, "  "),
      Token(ID, 115, 121, "classK"),
      Token(WHITE_SPACE, 121, 122, " "),
      Token(DOTTED_LINE, 122, 124, ".."),
      Token(DEPENDENCY_END, 124, 125, ">"),
      Token(WHITE_SPACE, 125, 126, " "),
      Token(ID, 126, 132, "classL"),
      Token(EOL, 132, 133, "\n"),
      Token(WHITE_SPACE, 133, 135, "  "),
      Token(ID, 135, 141, "classM"),
      Token(WHITE_SPACE, 141, 142, " "),
      Token(DOTTED_LINE, 142, 144, ".."),
      Token(EXTENSION_END, 144, 146, "|>"),
      Token(WHITE_SPACE, 146, 147, " "),
      Token(ID, 147, 153, "classN"),
      Token(EOL, 153, 154, "\n"),
      Token(WHITE_SPACE, 154, 156, "  "),
      Token(ID, 156, 162, "classO"),
      Token(WHITE_SPACE, 162, 163, " "),
      Token(DOTTED_LINE, 163, 165, ".."),
      Token(WHITE_SPACE, 165, 166, " "),
      Token(ID, 166, 172, "classP")
    )
    doTest(content, expected)
  }

  fun `test class two-way relationship`() {
    val content = """
    classDiagram
      Animal <|--|> Zebra
    """.trimIndent()
    val expected = listOf(
      Token(CLASS_DIAGRAM, 0, 12, "classDiagram"),
      Token(EOL, 12, 13, "\n"),
      Token(WHITE_SPACE, 13, 15, "  "),
      Token(ID, 15, 21, "Animal"),
      Token(WHITE_SPACE, 21, 22, " "),
      Token(EXTENSION_START, 22, 24, "<|"),
      Token(LINE, 24, 26, "--"),
      Token(EXTENSION_END, 26, 28, "|>"),
      Token(WHITE_SPACE, 28, 29, " "),
      Token(ID, 29, 34, "Zebra")
    )
    doTest(content, expected)
  }

  fun `test class relationship with label`() {
    val content = """
    classDiagram
      classA <|-- classB : implements
    """.trimIndent()
    val expected = listOf(
      Token(CLASS_DIAGRAM, 0, 12, "classDiagram"),
      Token(EOL, 12, 13, "\n"),
      Token(WHITE_SPACE, 13, 15, "  "),
      Token(ID, 15, 21, "classA"),
      Token(WHITE_SPACE, 21, 22, " "),
      Token(EXTENSION_START, 22, 24, "<|"),
      Token(LINE, 24, 26, "--"),
      Token(WHITE_SPACE, 26, 27, " "),
      Token(ID, 27, 33, "classB"),
      Token(WHITE_SPACE, 33, 34, " "),
      Token(COLON, 34, 35, ":"),
      Token(LABEL, 35, 46, " implements")
    )
    doTest(content, expected)
  }

  fun `test class relationship with cardinality`() {
    val content = """
    classDiagram
      Customer "1" --> "*" Ticket
      Student "1" --> "1..*" Course
      Galaxy --> "many" Star : Contains
    """.trimIndent()
    val expected = listOf(
      Token(CLASS_DIAGRAM, 0, 12, "classDiagram"),
      Token(EOL, 12, 13, "\n"),
      Token(WHITE_SPACE, 13, 15, "  "),
      Token(ID, 15, 23, "Customer"),
      Token(WHITE_SPACE, 23, 24, " "),
      Token(DOUBLE_QUOTE, 24, 25, "\""),
      Token(STRING_VALUE, 25, 26, "1"),
      Token(DOUBLE_QUOTE, 26, 27, "\""),
      Token(WHITE_SPACE, 27, 28, " "),
      Token(LINE, 28, 30, "--"),
      Token(DEPENDENCY_END, 30, 31, ">"),
      Token(WHITE_SPACE, 31, 32, " "),
      Token(DOUBLE_QUOTE, 32, 33, "\""),
      Token(STRING_VALUE, 33, 34, "*"),
      Token(DOUBLE_QUOTE, 34, 35, "\""),
      Token(WHITE_SPACE, 35, 36, " "),
      Token(ID, 36, 42, "Ticket"),
      Token(EOL, 42, 43, "\n"),
      Token(WHITE_SPACE, 43, 45, "  "),
      Token(ID, 45, 52, "Student"),
      Token(WHITE_SPACE, 52, 53, " "),
      Token(DOUBLE_QUOTE, 53, 54, "\""),
      Token(STRING_VALUE, 54, 55, "1"),
      Token(DOUBLE_QUOTE, 55, 56, "\""),
      Token(WHITE_SPACE, 56, 57, " "),
      Token(LINE, 57, 59, "--"),
      Token(DEPENDENCY_END, 59, 60, ">"),
      Token(WHITE_SPACE, 60, 61, " "),
      Token(DOUBLE_QUOTE, 61, 62, "\""),
      Token(STRING_VALUE, 62, 66, "1..*"),
      Token(DOUBLE_QUOTE, 66, 67, "\""),
      Token(WHITE_SPACE, 67, 68, " "),
      Token(ID, 68, 74, "Course"),
      Token(EOL, 74, 75, "\n"),
      Token(WHITE_SPACE, 75, 77, "  "),
      Token(ID, 77, 83, "Galaxy"),
      Token(WHITE_SPACE, 83, 84, " "),
      Token(LINE, 84, 86, "--"),
      Token(DEPENDENCY_END, 86, 87, ">"),
      Token(WHITE_SPACE, 87, 88, " "),
      Token(DOUBLE_QUOTE, 88, 89, "\""),
      Token(STRING_VALUE, 89, 93, "many"),
      Token(DOUBLE_QUOTE, 93, 94, "\""),
      Token(WHITE_SPACE, 94, 95, " "),
      Token(ID, 95, 99, "Star"),
      Token(WHITE_SPACE, 99, 100, " "),
      Token(COLON, 100, 101, ":"),
      Token(LABEL, 101, 110, " Contains")
    )
    doTest(content, expected)
  }

  fun `test class with annotation`() {
    val content = """
    classDiagram
      class Shape
      <<interface>> Shape
    """.trimIndent()
    val expected = listOf(
      Token(CLASS_DIAGRAM, 0, 12, "classDiagram"),
      Token(EOL, 12, 13, "\n"),
      Token(WHITE_SPACE, 13, 15, "  "),
      Token(CLASS, 15, 20, "class"),
      Token(WHITE_SPACE, 20, 21, " "),
      Token(ID, 21, 26, "Shape"),
      Token(EOL, 26, 27, "\n"),
      Token(WHITE_SPACE, 27, 29, "  "),
      Token(ANNOTATION_START, 29, 31, "<<"),
      Token(ANNOTATION_VALUE, 31, 40, "interface"),
      Token(ANNOTATION_END, 40, 42, ">>"),
      Token(WHITE_SPACE, 42, 43, " "),
      Token(ID, 43, 48, "Shape")
    )
    doTest(content, expected)
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
    val expected = listOf(
      Token(CLASS_DIAGRAM, 0, 12, "classDiagram"),
      Token(EOL, 12, 13, "\n"),
      Token(WHITE_SPACE, 13, 15, "  "),
      Token(CLASS, 15, 20, "class"),
      Token(WHITE_SPACE, 20, 21, " "),
      Token(ID, 21, 26, "Color"),
      Token(WHITE_SPACE, 26, 27, " "),
      Token(OPEN_CURLY, 27, 28, "{"),
      Token(EOL, 28, 29, "\n"),
      Token(WHITE_SPACE, 29, 33, "    "),
      Token(ANNOTATION_START, 33, 35, "<<"),
      Token(ANNOTATION_VALUE, 35, 46, "enumeration"),
      Token(ANNOTATION_END, 46, 48, ">>"),
      Token(EOL, 48, 49, "\n"),
      Token(WHITE_SPACE, 49, 53, "    "),
      Token(ID, 53, 56, "RED"),
      Token(EOL, 56, 57, "\n"),
      Token(WHITE_SPACE, 57, 61, "    "),
      Token(ID, 61, 65, "BLUE"),
      Token(EOL, 65, 66, "\n"),
      Token(WHITE_SPACE, 66, 68, "  "),
      Token(CLOSE_CURLY, 68, 69, "}")
    )
    doTest(content, expected)
  }

  fun `test class with direction`() {
    val content = """
    classDiagram
      direction RL
      class Student {
        -idCard : IdCard
      }
    """.trimIndent()
    val expected = listOf(
      Token(CLASS_DIAGRAM, 0, 12, "classDiagram"),
      Token(EOL, 12, 13, "\n"),
      Token(WHITE_SPACE, 13, 15, "  "),
      Token(DIRECTION, 15, 24, "direction"),
      Token(WHITE_SPACE, 24, 25, " "),
      Token(DIR, 25, 27, "RL"),
      Token(EOL, 27, 28, "\n"),
      Token(WHITE_SPACE, 28, 30, "  "),
      Token(CLASS, 30, 35, "class"),
      Token(WHITE_SPACE, 35, 36, " "),
      Token(ID, 36, 43, "Student"),
      Token(WHITE_SPACE, 43, 44, " "),
      Token(OPEN_CURLY, 44, 45, "{"),
      Token(EOL, 45, 46, "\n"),
      Token(WHITE_SPACE, 46, 50, "    "),
      Token(MINUS, 50, 51, "-"),
      Token(ID, 51, 57, "idCard"),
      Token(WHITE_SPACE, 57, 58, " "),
      Token(COLON, 58, 59, ":"),
      Token(WHITE_SPACE, 59, 60, " "),
      Token(ID, 60, 66, "IdCard"),
      Token(EOL, 66, 67, "\n"),
      Token(WHITE_SPACE, 67, 69, "  "),
      Token(CLOSE_CURLY, 69, 70, "}")
    )
    doTest(content, expected)
  }

  fun `test class with style`() {
    val content = """
    classDiagram
      class Animal:::cssClass {
        -int sizeInFeet
        -canEat()
      }
    """.trimIndent()
    val expected = listOf(
      Token(CLASS_DIAGRAM, 0, 12, "classDiagram"),
      Token(EOL, 12, 13, "\n"),
      Token(WHITE_SPACE, 13, 15, "  "),
      Token(CLASS, 15, 20, "class"),
      Token(WHITE_SPACE, 20, 21, " "),
      Token(ID, 21, 27, "Animal"),
      Token(STYLE_SEPARATOR, 27, 30, ":::"),
      Token(ID, 30, 38, "cssClass"),
      Token(WHITE_SPACE, 38, 39, " "),
      Token(OPEN_CURLY, 39, 40, "{"),
      Token(EOL, 40, 41, "\n"),
      Token(WHITE_SPACE, 41, 45, "    "),
      Token(MINUS, 45, 46, "-"),
      Token(ID, 46, 49, "int"),
      Token(WHITE_SPACE, 49, 50, " "),
      Token(ID, 50, 60, "sizeInFeet"),
      Token(EOL, 60, 61, "\n"),
      Token(WHITE_SPACE, 61, 65, "    "),
      Token(MINUS, 65, 66, "-"),
      Token(ID, 66, 72, "canEat"),
      Token(OPEN_ROUND, 72, 73, "("),
      Token(CLOSE_ROUND, 73, 74, ")"),
      Token(EOL, 74, 75, "\n"),
      Token(WHITE_SPACE, 75, 77, "  "),
      Token(CLOSE_CURLY, 77, 78, "}")
    )
    doTest(content, expected)
  }
}
