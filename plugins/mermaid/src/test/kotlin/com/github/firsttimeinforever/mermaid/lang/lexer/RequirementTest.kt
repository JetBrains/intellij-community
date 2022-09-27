package com.github.firsttimeinforever.mermaid.lang.lexer

import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.CLOSE_CURLY
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COLON
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.DOUBLE_QUOTE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.EOL
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ID
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ID_KEYWORD
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.LABEL
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.OPEN_CURLY
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.ANALYSIS
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.ARROW_LEFT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.ARROW_RIGHT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.CONTAINS
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.COPIES
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.DEMONSTRATION
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.DERIVES
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.DESIGN_CONSTRAINT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.DOCREF
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.ELEMENT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.FUNCTIONAL_REQUIREMENT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.HIGH
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.INSPECTION
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.INTERFACE_REQUIREMENT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.LOW
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.MEDIUM
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.PERFORMANCE_REQUIREMENT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.PHYSICAL_REQUIREMENT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.REFINES
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.REQUIREMENT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.REQUIREMENT_DIAGRAM
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.REQ_LINE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.RISK
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.SATISFIES
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.TEST
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.TEXT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.TRACES
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.VERIFIES
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.VERIFY_METHOD
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.STRING_VALUE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.TYPE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.WHITE_SPACE

class RequirementTest : MermaidLexerTestCase() {
  override val diagramName: String
    get() = "requirement"

  fun `test simple class requirement diagram`() {
    val content = """
    requirementDiagram
      requirement test_req {
        id: 1
        text: the test text.
        risk: high
        verifymethod: test
      }
      element test_entity {
        type: simulation
      }
  
      test_entity - satisfies -> test_req
    """.trimIndent()
    doTest(content)
  }

  fun `test full complex requirement diagram`() {
    val content = """
    requirementDiagram
      requirement test_req {
        id: 1
        text: the test text.
        risk: high
        verifymethod: test
      }
  
      functionalRequirement test_req2 {
        id: 1.1
        text: the second test text.
        risk: low
        verifymethod: inspection
      }
  
      performanceRequirement test_req3 {
        id: 1.2
        text: the third test text.
        risk: medium
        verifymethod: demonstration
      }
  
      interfaceRequirement test_req4 {
        id: 1.2.1
        text: the fourth test text.
        risk: medium
        verifymethod: analysis
      }
  
      physicalRequirement test_req5 {
        id: 1.2.2
        text: the fifth test text.
        risk: medium
        verifymethod: analysis
      }
  
      designConstraint test_req6 {
        id: 1.2.3
        text: the sixth test text.
        risk: medium
        verifymethod: analysis
      }
  
      element test_entity {
        type: simulation
      }
  
      element test_entity2 {
        type: word doc
        docRef: reqs/test_entity
      }
  
      element test_entity3 {
        type: "test suite"
        docRef: github.com/all_the_tests
      }
    
      test_entity - satisfies -> test_req2
      test_req - traces -> test_req2
      test_req - contains -> test_req3
      test_req3 - contains -> test_req4
      test_req4 - derives -> test_req5
      test_req5 - refines -> test_req6
      test_entity3 - verifies -> test_req5
      test_req <- copies - test_entity2
    """.trimIndent()
    doTest(content)
  }
}
