package com.intellij.mermaid.lang.lexer

class ClassDiagramTest : MermaidLexerTestCase() {
  override val diagramName: String
    get() = "class"

  fun `test simple class definition`() {
    val content = """
    classDiagram
      class BankAccount
      BankAccount : +String owner
      BankAccount : +deposit(amount)
    """.trimIndent()
    doTest(content)
  }

  fun `test class definition in brackets`() {
    val content = """
    classDiagram
      class BankAccount {
        +String owner
        +deposit(amount) bool
      }
    """.trimIndent()
    doTest(content)
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
    doTest(content)
  }

  fun `test identifiers at end of member`() {
    val content = """
    classDiagram
      class BankAccount
      BankAccount : +someAbstractMethod()*
      BankAccount : +someStaticMethod()$
    """.trimIndent()
    doTest(content)
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
    doTest(content)
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
    doTest(content)
  }

  fun `test class two-way relationship`() {
    val content = """
    classDiagram
      Animal <|--|> Zebra
    """.trimIndent()
    doTest(content)
  }

  fun `test class relationship with label`() {
    val content = """
    classDiagram
      classA <|-- classB : implements
    """.trimIndent()
    doTest(content)
  }

  fun `test class relationship with cardinality`() {
    val content = """
    classDiagram
      Customer "1" --> "*" Ticket
      Student "1" --> "1..*" Course
      Galaxy --> "many" Star : Contains
    """.trimIndent()
    doTest(content)
  }

  fun `test class with annotation`() {
    val content = """
    classDiagram
      class Shape
      <<interface>> Shape
      
      <<abstract>> Shape2
      class Shape2
    """.trimIndent()
    doTest(content)
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
    doTest(content)
  }

  fun `test class with direction`() {
    val content = """
    classDiagram
      direction RL
      class Student {
        -idCard : IdCard
      }
    """.trimIndent()
    doTest(content)
  }

  fun `test class with style`() {
    val content = """
    classDiagram
      class Animal:::cssClass {
        -int sizeInFeet
        -canEat()
      }
    """.trimIndent()
    doTest(content)
  }

  fun `test click statements`() {
    val content = """
    classDiagram
      class Shape
      click Shape href "https://www.github.com" "This is a tooltip for a link"
      click Shape call callbackFunction() "This is a tooltip for a callback"
      click Shape href "https://www.github.com" 
      click Shape call callbackFunction()
      link Shape "https://www.github.com" "This is a tooltip for a link"
      callback Shape "callbackFunction" "This is a tooltip for a callback"
    """.trimIndent()
    doTest(content)
  }

  fun `test class attributes with square parenthesis`() {
    val content = """
    classDiagram
      class Class1
      class Class2
      Class1 : Object[] elementData
      Class2 : Obj ect[] element Data
      class Class3 {
        Object[] elementData
      }
      class Class4 {
        Obj ect[] element Data
      }
    """.trimIndent()
    doTest(content)
  }

  fun `test complex attribute`() {
    val content = """
    classDiagram
      class C1
      C1: met  <>.h[]   id+{},((f) ()()
      
      class C2 {
        met  <;>.h[]   id:+,((f) ()()
      }
    """.trimIndent()
    doTest(content)
  }

  fun `test notes`() {
    val content = """
    classDiagram
      note "line1\nline2"
      
      class A
      note for A "line1\nline2"
      
      class B {
      }
      note for B "line1\nline2"
      
      C --> D
      note for C "line1\nline2"
    """.trimIndent()
    doTest(content)
  }

  fun `test class labels`() {
    val content = """
    classDiagram
      class Animal["Animal with a label"]
      class Car["Car with *! symbols"]
      Animal --> Car
    """.trimIndent()
    doTest(content)
  }

  fun `test backticks`() {
    val content = """
    classDiagram
      class `Animal Class!`
      class `Car Class`
      `Animal Class!` --> `Car Class`
    """.trimIndent()
    doTest(content)
  }
}
