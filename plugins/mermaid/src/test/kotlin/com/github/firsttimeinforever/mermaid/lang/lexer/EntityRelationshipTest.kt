package com.github.firsttimeinforever.mermaid.lang.lexer

import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.CLOSE_CURLY
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COLON
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.DOUBLE_QUOTE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.EOL
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.EntityRelationship.ATTR_KEY
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.EntityRelationship.ENTITY_RELATIONSHIP
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.EntityRelationship.IDENTIFYING
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.EntityRelationship.NON_IDENTIFYING
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.EntityRelationship.ONE_OR_MORE_LEFT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.EntityRelationship.ONE_OR_MORE_RIGHT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.EntityRelationship.ONLY_ONE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.EntityRelationship.ZERO_OR_MORE_RIGHT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ID
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.LABEL
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.OPEN_CURLY
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.STRING_VALUE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.WHITE_SPACE

class EntityRelationshipTest : MermaidLexerTestCase() {
  fun `test simple entity relationship`() {
    val content = """
    erDiagram
      CUSTOMER ||--o{ ORDER : places
      ORDER ||--|{ LINE-ITEM : contains
      CUSTOMER }|..|{ DELIVERY-ADDRESS : uses
    """.trimIndent()
    val expected = listOf(
      Token(ENTITY_RELATIONSHIP, 0, 9, "erDiagram"),
      Token(EOL, 9, 10, "\n"),
      Token(WHITE_SPACE, 10, 12, "  "),
      Token(ID, 12, 20, "CUSTOMER"),
      Token(WHITE_SPACE, 20, 21, " "),
      Token(ONLY_ONE, 21, 23, "||"),
      Token(IDENTIFYING, 23, 25, "--"),
      Token(ZERO_OR_MORE_RIGHT, 25, 27, "o{"),
      Token(WHITE_SPACE, 27, 28, " "),
      Token(ID, 28, 33, "ORDER"),
      Token(WHITE_SPACE, 33, 34, " "),
      Token(COLON, 34, 35, ":"),
      Token(WHITE_SPACE, 35, 36, " "),
      Token(LABEL, 36, 42, "places"),
      Token(EOL, 42, 43, "\n"),
      Token(WHITE_SPACE, 43, 45, "  "),
      Token(ID, 45, 50, "ORDER"),
      Token(WHITE_SPACE, 50, 51, " "),
      Token(ONLY_ONE, 51, 53, "||"),
      Token(IDENTIFYING, 53, 55, "--"),
      Token(ONE_OR_MORE_RIGHT, 55, 57, "|{"),
      Token(WHITE_SPACE, 57, 58, " "),
      Token(ID, 58, 67, "LINE-ITEM"),
      Token(WHITE_SPACE, 67, 68, " "),
      Token(COLON, 68, 69, ":"),
      Token(WHITE_SPACE, 69, 70, " "),
      Token(LABEL, 70, 78, "contains"),
      Token(EOL, 78, 79, "\n"),
      Token(WHITE_SPACE, 79, 81, "  "),
      Token(ID, 81, 89, "CUSTOMER"),
      Token(WHITE_SPACE, 89, 90, " "),
      Token(ONE_OR_MORE_LEFT, 90, 92, "}|"),
      Token(NON_IDENTIFYING, 92, 94, ".."),
      Token(ONE_OR_MORE_RIGHT, 94, 96, "|{"),
      Token(WHITE_SPACE, 96, 97, " "),
      Token(ID, 97, 113, "DELIVERY-ADDRESS"),
      Token(WHITE_SPACE, 113, 114, " "),
      Token(COLON, 114, 115, ":"),
      Token(WHITE_SPACE, 115, 116, " "),
      Token(LABEL, 116, 120, "uses")
    )
    doTest(content, expected)
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
    val expected = listOf(
      Token(ENTITY_RELATIONSHIP, 0, 9, "erDiagram"),
      Token(EOL, 9, 10, "\n"),
      Token(WHITE_SPACE, 10, 12, "  "),
      Token(ID, 12, 20, "CUSTOMER"),
      Token(WHITE_SPACE, 20, 21, " "),
      Token(ONLY_ONE, 21, 23, "||"),
      Token(IDENTIFYING, 23, 25, "--"),
      Token(ZERO_OR_MORE_RIGHT, 25, 27, "o{"),
      Token(WHITE_SPACE, 27, 28, " "),
      Token(ID, 28, 33, "ORDER"),
      Token(WHITE_SPACE, 33, 34, " "),
      Token(COLON, 34, 35, ":"),
      Token(WHITE_SPACE, 35, 36, " "),
      Token(LABEL, 36, 42, "places"),
      Token(EOL, 42, 43, "\n"),
      Token(WHITE_SPACE, 43, 45, "  "),
      Token(ID, 45, 53, "CUSTOMER"),
      Token(WHITE_SPACE, 53, 54, " "),
      Token(OPEN_CURLY, 54, 55, "{"),
      Token(EOL, 55, 56, "\n"),
      Token(WHITE_SPACE, 56, 60, "    "),
      Token(ID, 60, 66, "string"),
      Token(WHITE_SPACE, 66, 67, " "),
      Token(ID, 67, 71, "name"),
      Token(EOL, 71, 72, "\n"),
      Token(WHITE_SPACE, 72, 76, "    "),
      Token(ID, 76, 82, "string"),
      Token(WHITE_SPACE, 82, 83, " "),
      Token(ID, 83, 93, "custNumber"),
      Token(EOL, 93, 94, "\n"),
      Token(WHITE_SPACE, 94, 98, "    "),
      Token(ID, 98, 104, "string"),
      Token(WHITE_SPACE, 104, 105, " "),
      Token(ID, 105, 111, "sector"),
      Token(EOL, 111, 112, "\n"),
      Token(WHITE_SPACE, 112, 114, "  "),
      Token(CLOSE_CURLY, 114, 115, "}"),
      Token(EOL, 115, 116, "\n"),
      Token(WHITE_SPACE, 116, 118, "  "),
      Token(ID, 118, 123, "ORDER"),
      Token(WHITE_SPACE, 123, 124, " "),
      Token(ONLY_ONE, 124, 126, "||"),
      Token(IDENTIFYING, 126, 128, "--"),
      Token(ONE_OR_MORE_RIGHT, 128, 130, "|{"),
      Token(WHITE_SPACE, 130, 131, " "),
      Token(ID, 131, 140, "LINE-ITEM"),
      Token(WHITE_SPACE, 140, 141, " "),
      Token(COLON, 141, 142, ":"),
      Token(WHITE_SPACE, 142, 143, " "),
      Token(LABEL, 143, 151, "contains")
    )
    doTest(content, expected)
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
    val expected = listOf(
      Token(ENTITY_RELATIONSHIP, 0, 9, "erDiagram"),
      Token(EOL, 9, 10, "\n"),
      Token(WHITE_SPACE, 10, 12, "  "),
      Token(ID, 12, 15, "CAR"),
      Token(WHITE_SPACE, 15, 16, " "),
      Token(ONLY_ONE, 16, 18, "||"),
      Token(IDENTIFYING, 18, 20, "--"),
      Token(ZERO_OR_MORE_RIGHT, 20, 22, "o{"),
      Token(WHITE_SPACE, 22, 23, " "),
      Token(ID, 23, 35, "NAMED-DRIVER"),
      Token(WHITE_SPACE, 35, 36, " "),
      Token(COLON, 36, 37, ":"),
      Token(WHITE_SPACE, 37, 38, " "),
      Token(LABEL, 38, 44, "allows"),
      Token(EOL, 44, 45, "\n"),
      Token(WHITE_SPACE, 45, 47, "  "),
      Token(ID, 47, 50, "CAR"),
      Token(WHITE_SPACE, 50, 51, " "),
      Token(OPEN_CURLY, 51, 52, "{"),
      Token(EOL, 52, 53, "\n"),
      Token(WHITE_SPACE, 53, 57, "    "),
      Token(ID, 57, 63, "string"),
      Token(WHITE_SPACE, 63, 64, " "),
      Token(ID, 64, 77, "allowedDriver"),
      Token(WHITE_SPACE, 77, 78, " "),
      Token(ATTR_KEY, 78, 80, "FK"),
      Token(WHITE_SPACE, 80, 81, " "),
      Token(DOUBLE_QUOTE, 81, 82, "\""),
      Token(STRING_VALUE, 82, 115, "The license of the allowed driver"),
      Token(DOUBLE_QUOTE, 115, 116, "\""),
      Token(EOL, 116, 117, "\n"),
      Token(WHITE_SPACE, 117, 121, "    "),
      Token(ID, 121, 127, "string"),
      Token(WHITE_SPACE, 127, 128, " "),
      Token(ID, 128, 146, "registrationNumber"),
      Token(WHITE_SPACE, 146, 147, " "),
      Token(ATTR_KEY, 147, 149, "PK"),
      Token(EOL, 149, 150, "\n"),
      Token(WHITE_SPACE, 150, 154, "    "),
      Token(ID, 154, 160, "string"),
      Token(WHITE_SPACE, 160, 161, " "),
      Token(ID, 161, 165, "make"),
      Token(WHITE_SPACE, 165, 166, " "),
      Token(DOUBLE_QUOTE, 166, 167, "\""),
      Token(STRING_VALUE, 167, 174, "comment"),
      Token(DOUBLE_QUOTE, 174, 175, "\""),
      Token(EOL, 175, 176, "\n"),
      Token(WHITE_SPACE, 176, 180, "    "),
      Token(ID, 180, 186, "string"),
      Token(WHITE_SPACE, 186, 187, " "),
      Token(ID, 187, 192, "model"),
      Token(EOL, 192, 193, "\n"),
      Token(WHITE_SPACE, 193, 195, "  "),
      Token(CLOSE_CURLY, 195, 196, "}"),
      Token(EOL, 196, 197, "\n"),
      Token(WHITE_SPACE, 197, 199, "  "),
      Token(ID, 199, 205, "PERSON"),
      Token(WHITE_SPACE, 205, 206, " "),
      Token(ONLY_ONE, 206, 208, "||"),
      Token(IDENTIFYING, 208, 210, "--"),
      Token(ZERO_OR_MORE_RIGHT, 210, 212, "o{"),
      Token(WHITE_SPACE, 212, 213, " "),
      Token(ID, 213, 225, "NAMED-DRIVER"),
      Token(WHITE_SPACE, 225, 226, " "),
      Token(COLON, 226, 227, ":"),
      Token(WHITE_SPACE, 227, 228, " "),
      Token(LABEL, 228, 230, "is")
    )
    doTest(content, expected)
  }

  fun `test entity relationship with double quoted label`() {
    val content = """
    erDiagram
      CUSTOMER ||--o{ ORDER : "pla ce s"
      ORDER ||--|{ LINE-ITEM : ""
    """.trimIndent()
    val expected = listOf(
      Token(ENTITY_RELATIONSHIP, 0, 9, "erDiagram"),
      Token(EOL, 9, 10, "\n"),
      Token(WHITE_SPACE, 10, 12, "  "),
      Token(ID, 12, 20, "CUSTOMER"),
      Token(WHITE_SPACE, 20, 21, " "),
      Token(ONLY_ONE, 21, 23, "||"),
      Token(IDENTIFYING, 23, 25, "--"),
      Token(ZERO_OR_MORE_RIGHT, 25, 27, "o{"),
      Token(WHITE_SPACE, 27, 28, " "),
      Token(ID, 28, 33, "ORDER"),
      Token(WHITE_SPACE, 33, 34, " "),
      Token(COLON, 34, 35, ":"),
      Token(WHITE_SPACE, 35, 36, " "),
      Token(DOUBLE_QUOTE, 36, 37, "\""),
      Token(STRING_VALUE, 37, 45, "pla ce s"),
      Token(DOUBLE_QUOTE, 45, 46, "\""),
      Token(EOL, 46, 47, "\n"),
      Token(WHITE_SPACE, 47, 49, "  "),
      Token(ID, 49, 54, "ORDER"),
      Token(WHITE_SPACE, 54, 55, " "),
      Token(ONLY_ONE, 55, 57, "||"),
      Token(IDENTIFYING, 57, 59, "--"),
      Token(ONE_OR_MORE_RIGHT, 59, 61, "|{"),
      Token(WHITE_SPACE, 61, 62, " "),
      Token(ID, 62, 71, "LINE-ITEM"),
      Token(WHITE_SPACE, 71, 72, " "),
      Token(COLON, 72, 73, ":"),
      Token(WHITE_SPACE, 73, 74, " "),
      Token(DOUBLE_QUOTE, 74, 75, "\""),
      Token(DOUBLE_QUOTE, 75, 76, "\"")
    )
    doTest(content, expected)
  }
}
