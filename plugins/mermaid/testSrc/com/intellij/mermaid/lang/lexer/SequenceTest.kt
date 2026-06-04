package com.intellij.mermaid.lang.lexer

class SequenceTest : MermaidLexerTestCase() {
  override val diagramName: String
    get() = "sequence"

  fun `test simple sequence`() {
    val content = """
    sequenceDiagram
      participant A B as Alice B
      actor J as John
      A B --> J: Hello John, how are you? ; J -->> A B : Great! # And you? J -->> A B
    """.trimIndent()
    doTest(content)
  }

  fun `test sequence with activations`() {
    val content = """
    sequenceDiagram
      Alice->>John: Hello John, how are you?
      activate John
      John-->>Alice: Great!
      deactivate John
    """.trimIndent()
    doTest(content)
  }

  fun `test sequence with short activations`() {
    val content = """
    sequenceDiagram
      Alice->>+John: Hello John, how are you?
      John-->>-Alice: Great!
    """.trimIndent()
    doTest(content)
  }

  fun `test sequence with notes`() {
    val content = """
    sequenceDiagram
      participant John
      Note right of John: Text in note
      Alice->John: Hello John, how are you?
      Note over Alice,John: A typical interaction
    """.trimIndent()
    doTest(content)
  }

  fun `test sequence with comments`() {
    val content = """
    sequenceDiagram %% this is a comment
      actor Alice %% this is not a comment
      Alice->>John: Hello John, how are you? %% this is not a comment
      %% this is a comment
      John-->>Alice: Great!
    """.trimIndent()
    doTest(content)
  }

  fun `test sequence with json formatted link`() {
    val content = """
    sequenceDiagram
      participant Alice
      links Alice: {"Dashboard": "https://dashboard.contoso.com/alice", "Wiki": "https://wiki.contoso.com/alice"}
    """.trimIndent()
    doTest(content)
  }

  fun `test sequence with loop`() {
    val content = """
    sequenceDiagram
      Alice->John: Hello John, how are you?
      loop Every minute
          participant Bob; John-->Alice: Great!; Alice -> Bob: WOWO
      end
    """.trimIndent()
    doTest(content)
  }

  fun `test autonumber`() {
    val content = """
    sequenceDiagram
      autonumber 
      autonumber 5
      autonumber 5 2
      autonumber off
    """.trimIndent()
    doTest(content)
  }

  fun `test critical region`() {
    val content = """
    sequenceDiagram
      critical Establish a connection to the DB
        Service-->DB: connect
      option Network timeout
        Service-->Service: Log error
      option Credentials rejected
        Service-->Service: Log different error
      end
    """.trimIndent()
    doTest(content)
  }

  fun `test break`() {
    val content = """
    sequenceDiagram
      Consumer-->API: Book something
      API-->BookingService: Start booking process
      break when the booking process fails
        API-->Consumer: show failure
      end
      API-->BillingService: Start billing process
    """.trimIndent()
    doTest(content)
  }

  fun `test actors names with dash`() {
    val content = """
    sequenceDiagram
      actor A as Alice
      actor J-J as JohnJunior
      A -->> J-J
    """.trimIndent()
    doTest(content)
  }

  fun `test box`() {
    val content = """
    sequenceDiagram
      box Purple Alice & John
        participant A
        participant J
      end
      box Another Group
        participant B
        participant C
      end
      A->>J: Hello John, how are you?
      J->>A: Great!
      A->>B: Hello Bob, how is Charly ?
      B->>C: Hello Charly, how are you?
    """.trimIndent()
    doTest(content)
  }

  fun `test par_over`() {
    val content = """
    sequenceDiagram
      participant Alice
      participant Bob
      participant John
      par_over Section title
        Alice ->> Bob: Message 1<br>Second line
        Bob ->> John: Message 2
      end
      par_over Two line<br>section title
        Note over Alice: Alice note
        Note over Bob: Bob note<br>Second line
        Note over John: John note
      end
      par_over Mixed section
        Alice ->> Bob: Message 1
        Note left of Bob: Alice/Bob Note
      end
    """.trimIndent()
    doTest(content)
  }

  fun `test different signals and spacing`() {
    val content = """
    sequenceDiagram
      Bob-->Alice: I am good thanks!
      Bob -->Alice: I am good thanks!
      Bob--> Alice: I am good thanks!
      Bob --> Alice: I am good thanks!
    
      Bob-->>Alice: I am good thanks!
      Bob -->>Alice: I am good thanks!
      Bob-->> Alice: I am good thanks!
      Bob -->> Alice: I am good thanks!
    
      Bob--xAlice: I am good thanks!
      Bob --xAlice: I am good thanks!
      Bob--x Alice: I am good thanks!
      Bob --x Alice: I am good thanks!
    
      Bob--)Alice: I am good thanks!
      Bob --)Alice: I am good thanks!
      Bob--) Alice: I am good thanks!
      Bob --) Alice: I am good thanks!
    """.trimIndent()
    doTest(content)
  }

  fun `test directives`() {
    val content = """
    %%{init: { "theme": "forest"}}%%
    sequenceDiagram
      %%{init: { "theme": "forest"}}%%
      Alice->>John: Hello John, how are you?
      %%{init: { "theme": "forest"}}%%
      John-->>Alice: Great!
      Alice-)John: See you later!
    """.trimIndent()
    doTest(content)
  }

  fun `test entity codes`() {
    val content = """
    sequenceDiagram
      A->>B: I #9829; you!
      B->>A: I #9829; you #infin; times more!
    """.trimIndent()
    doTest(content)
  }
}
