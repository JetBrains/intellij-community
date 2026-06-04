package com.intellij.mermaid.lang.lexer

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

  fun `test entity names with double quotes`() {
    val content = """
    erDiagram
      "Service::User" {
        string name
        string password
      }
    
      "Service::Log" {
        string message
      }
    
      "Service::User" }o--o{ "Service::Log" : has_many
    """.trimIndent()
    doTest(content)
  }

  fun `test cardinality aliases`() {
    val content = """
    erDiagram
      CUSTOMER only one -- zero or many ORDER : places
      ORDER one to one or more LINE-ITEM : contains
      CUSTOMER }| optionally to |{ DELIVERY-ADDRESS : uses
    """.trimIndent()
    doTest(content)
  }

  fun `test attr keys`() {
    val content = """
    erDiagram
      NAMED-DRIVER {
        string carRegistrationNumber PK, FK
        string driverLicence PK, FK
      }
    """.trimIndent()
    doTest(content)
  }

  fun `test parent-child relationship`() {
    val content = """
    erDiagram
      PROJECT u--|{ TEAM_MEMBER : parent
      TEAM_MEMBER }|--u PROJECT : child
    """.trimIndent()
    doTest(content)
  }

  fun `test keyword in attribute`() {
    val content = """
    erDiagram
      BOOK{string *title}
    """.trimIndent()
    doTest(content)
  }

  fun `test entity alias`() {
    val content = """
    erDiagram
      p[Person] {
        varchar(64) firstName
        varchar(64) lastName
      }
      c["Customer Account"] {
        varchar(128) email
      }
      p ||--o| c: has
    """.trimIndent()
    doTest(content)
  }
}
