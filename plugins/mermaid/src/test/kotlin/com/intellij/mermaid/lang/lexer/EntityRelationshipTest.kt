package com.intellij.mermaid.lang.lexer

import com.intellij.mermaid.lang.lexer.MermaidTokens.CLOSE_CURLY
import com.intellij.mermaid.lang.lexer.MermaidTokens.COLON
import com.intellij.mermaid.lang.lexer.MermaidTokens.DOUBLE_QUOTE
import com.intellij.mermaid.lang.lexer.MermaidTokens.EOL
import com.intellij.mermaid.lang.lexer.MermaidTokens.EntityRelationship.ATTR_KEY
import com.intellij.mermaid.lang.lexer.MermaidTokens.EntityRelationship.ENTITY_RELATIONSHIP
import com.intellij.mermaid.lang.lexer.MermaidTokens.EntityRelationship.IDENTIFYING
import com.intellij.mermaid.lang.lexer.MermaidTokens.EntityRelationship.NON_IDENTIFYING
import com.intellij.mermaid.lang.lexer.MermaidTokens.EntityRelationship.ONE_OR_MORE_LEFT
import com.intellij.mermaid.lang.lexer.MermaidTokens.EntityRelationship.ONE_OR_MORE_RIGHT
import com.intellij.mermaid.lang.lexer.MermaidTokens.EntityRelationship.ONLY_ONE
import com.intellij.mermaid.lang.lexer.MermaidTokens.EntityRelationship.ZERO_OR_MORE_RIGHT
import com.intellij.mermaid.lang.lexer.MermaidTokens.ID
import com.intellij.mermaid.lang.lexer.MermaidTokens.LABEL
import com.intellij.mermaid.lang.lexer.MermaidTokens.OPEN_CURLY
import com.intellij.mermaid.lang.lexer.MermaidTokens.STRING_VALUE
import com.intellij.mermaid.lang.lexer.MermaidTokens.WHITE_SPACE

class EntityRelationshipTest : MermaidLexerTestCase() {
  override val diagramName: String
    get() = "entityRelationship"

  fun `test simple entity relationship`() {
    val content = """
    erDiagram
      CUSTOMER ||--o{ ORDER : places
      ORDER ||--|{ LINE-ITEM : contains
      CUSTOMER }|..|{ DELIVERY-ADDRESS : uses
    """.trimIndent()
    doTest(content)
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
    doTest(content)
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
    doTest(content)
  }

  fun `test entity relationship with double quoted label`() {
    val content = """
    erDiagram
      CUSTOMER ||--o{ ORDER : "pla ce s"
      ORDER ||--|{ LINE-ITEM : ""
    """.trimIndent()
    doTest(content)
  }
}
