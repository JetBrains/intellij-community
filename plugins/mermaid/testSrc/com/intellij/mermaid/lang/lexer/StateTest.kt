package com.intellij.mermaid.lang.lexer

class StateTest : MermaidLexerTestCase() {
  override val diagramName: String
    get() = "state"

  fun `test different state definition`() {
    val content = """
    stateDiagram-v2
      s1
      state "This is a state description" as s2
      s2 : This is a state description
    """.trimIndent()
    doTest(content)
  }

  fun `test transitions`() {
    val content = """
    stateDiagram-v2
      s1 --> s2
      s3 --> s4: A transition
    """.trimIndent()
    doTest(content)
  }

  fun `test special start and end states`() {
    val content = """
    stateDiagram-v2
      [*] --> s1
      s1 --> [*]
    """.trimIndent()
    doTest(content)
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
    doTest(content)
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
    doTest(content)
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
    doTest(content)
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
    doTest(content)
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
    doTest(content)
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
    doTest(content)
  }

  fun `test class def`() {
    val content = """
    stateDiagram-v2
      [*] --> A
      A --> B: test({ foo#colon; 'far' })
      B --> [*]
      classDef badBadEvent fill:#f00,color:white,font-weight:bold 
      class B badBadEvent

      classDef yourState font-style:italic,font-weight:bold,fill:white

      yswsii: Your state with spaces in it
      [*] --> yswsii:::yourState
    """.trimIndent()
    doTest(content)
  }
}
