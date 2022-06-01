package com.github.firsttimeinforever.mermaid.lang.parser

class RequirementTest : MermaidParserTestCase() {
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
    val expectedTree = """
    Element(FILE)
    >PsiElement(REQUIREMENT_DIAGRAM)
    >Element(REQUIREMENT_DOCUMENT)
    >>Element(REQUIREMENT_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(REQUIREMENT_LINE)
    >>>Element(REQUIREMENT_DEF)
    >>>>Element(REQUIREMENT_TYPE)
    >>>>>PsiElement(REQUIREMENT)
    >>>>PsiWhiteSpace
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiElement(OPEN_CURLY)
    >>>>Element(REQUIREMENT_BODY)
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(ID_KEYWORD)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>PsiElement(LABEL)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(TEXT)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>PsiElement(LABEL)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(RISK)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>Element(RISK_LEVEL)
    >>>>>>>PsiElement(HIGH)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(VERIFY_METHOD)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>Element(VERIFY_TYPE)
    >>>>>>>PsiElement(TEST)
    >>>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>PsiElement(CLOSE_CURLY)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(REQUIREMENT_LINE)
    >>>Element(ELEMENT_DEF)
    >>>>PsiElement(ELEMENT)
    >>>>PsiWhiteSpace
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiElement(OPEN_CURLY)
    >>>>Element(ELEMENT_BODY)
    >>>>>Element(ELEMENT_BODY_LINE)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(ELEMENT_BODY_LINE)
    >>>>>>PsiElement(TYPE)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>PsiElement(LABEL)
    >>>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>PsiElement(CLOSE_CURLY)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(REQUIREMENT_LINE)
    >>>Element(RELATIONSHIP_DEF)
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiElement(REQ_LINE)
    >>>>PsiWhiteSpace
    >>>>Element(REQ_RELATIONSHIP)
    >>>>>PsiElement(SATISFIES)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ARROW_RIGHT)
    >>>>PsiWhiteSpace
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    """.trimIndent()
    doTest(content, expectedTree)
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
    val expectedTree = """
    Element(FILE)
    >PsiElement(REQUIREMENT_DIAGRAM)
    >Element(REQUIREMENT_DOCUMENT)
    >>Element(REQUIREMENT_LINE)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(REQUIREMENT_LINE)
    >>>Element(REQUIREMENT_DEF)
    >>>>Element(REQUIREMENT_TYPE)
    >>>>>PsiElement(REQUIREMENT)
    >>>>PsiWhiteSpace
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiElement(OPEN_CURLY)
    >>>>Element(REQUIREMENT_BODY)
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(ID_KEYWORD)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>PsiElement(LABEL)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(TEXT)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>PsiElement(LABEL)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(RISK)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>Element(RISK_LEVEL)
    >>>>>>>PsiElement(HIGH)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(VERIFY_METHOD)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>Element(VERIFY_TYPE)
    >>>>>>>PsiElement(TEST)
    >>>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>PsiElement(CLOSE_CURLY)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(REQUIREMENT_LINE)
    >>>Element(REQUIREMENT_DEF)
    >>>>Element(REQUIREMENT_TYPE)
    >>>>>PsiElement(FUNCTIONAL_REQUIREMENT)
    >>>>PsiWhiteSpace
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiElement(OPEN_CURLY)
    >>>>Element(REQUIREMENT_BODY)
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(ID_KEYWORD)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>PsiElement(LABEL)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(TEXT)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>PsiElement(LABEL)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(RISK)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>Element(RISK_LEVEL)
    >>>>>>>PsiElement(LOW)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(VERIFY_METHOD)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>Element(VERIFY_TYPE)
    >>>>>>>PsiElement(INSPECTION)
    >>>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>PsiElement(CLOSE_CURLY)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(REQUIREMENT_LINE)
    >>>Element(REQUIREMENT_DEF)
    >>>>Element(REQUIREMENT_TYPE)
    >>>>>PsiElement(PERFORMANCE_REQUIREMENT)
    >>>>PsiWhiteSpace
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiElement(OPEN_CURLY)
    >>>>Element(REQUIREMENT_BODY)
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(ID_KEYWORD)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>PsiElement(LABEL)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(TEXT)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>PsiElement(LABEL)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(RISK)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>Element(RISK_LEVEL)
    >>>>>>>PsiElement(MEDIUM)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(VERIFY_METHOD)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>Element(VERIFY_TYPE)
    >>>>>>>PsiElement(DEMONSTRATION)
    >>>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>PsiElement(CLOSE_CURLY)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(REQUIREMENT_LINE)
    >>>Element(REQUIREMENT_DEF)
    >>>>Element(REQUIREMENT_TYPE)
    >>>>>PsiElement(INTERFACE_REQUIREMENT)
    >>>>PsiWhiteSpace
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiElement(OPEN_CURLY)
    >>>>Element(REQUIREMENT_BODY)
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(ID_KEYWORD)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>PsiElement(LABEL)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(TEXT)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>PsiElement(LABEL)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(RISK)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>Element(RISK_LEVEL)
    >>>>>>>PsiElement(MEDIUM)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(VERIFY_METHOD)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>Element(VERIFY_TYPE)
    >>>>>>>PsiElement(ANALYSIS)
    >>>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>PsiElement(CLOSE_CURLY)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(REQUIREMENT_LINE)
    >>>Element(REQUIREMENT_DEF)
    >>>>Element(REQUIREMENT_TYPE)
    >>>>>PsiElement(PHYSICAL_REQUIREMENT)
    >>>>PsiWhiteSpace
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiElement(OPEN_CURLY)
    >>>>Element(REQUIREMENT_BODY)
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(ID_KEYWORD)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>PsiElement(LABEL)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(TEXT)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>PsiElement(LABEL)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(RISK)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>Element(RISK_LEVEL)
    >>>>>>>PsiElement(MEDIUM)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(VERIFY_METHOD)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>Element(VERIFY_TYPE)
    >>>>>>>PsiElement(ANALYSIS)
    >>>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>PsiElement(CLOSE_CURLY)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(REQUIREMENT_LINE)
    >>>Element(REQUIREMENT_DEF)
    >>>>Element(REQUIREMENT_TYPE)
    >>>>>PsiElement(DESIGN_CONSTRAINT)
    >>>>PsiWhiteSpace
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiElement(OPEN_CURLY)
    >>>>Element(REQUIREMENT_BODY)
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(ID_KEYWORD)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>PsiElement(LABEL)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(TEXT)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>PsiElement(LABEL)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(RISK)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>Element(RISK_LEVEL)
    >>>>>>>PsiElement(MEDIUM)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(REQUIREMENT_BODY_LINE)
    >>>>>>PsiElement(VERIFY_METHOD)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>Element(VERIFY_TYPE)
    >>>>>>>PsiElement(ANALYSIS)
    >>>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>PsiElement(CLOSE_CURLY)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(REQUIREMENT_LINE)
    >>>Element(ELEMENT_DEF)
    >>>>PsiElement(ELEMENT)
    >>>>PsiWhiteSpace
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiElement(OPEN_CURLY)
    >>>>Element(ELEMENT_BODY)
    >>>>>Element(ELEMENT_BODY_LINE)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(ELEMENT_BODY_LINE)
    >>>>>>PsiElement(TYPE)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>PsiElement(LABEL)
    >>>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>PsiElement(CLOSE_CURLY)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(REQUIREMENT_LINE)
    >>>Element(ELEMENT_DEF)
    >>>>PsiElement(ELEMENT)
    >>>>PsiWhiteSpace
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiElement(OPEN_CURLY)
    >>>>Element(ELEMENT_BODY)
    >>>>>Element(ELEMENT_BODY_LINE)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(ELEMENT_BODY_LINE)
    >>>>>>PsiElement(TYPE)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>PsiElement(LABEL)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(ELEMENT_BODY_LINE)
    >>>>>>PsiElement(DOCREF)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>PsiElement(LABEL)
    >>>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>PsiElement(CLOSE_CURLY)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(REQUIREMENT_LINE)
    >>>Element(ELEMENT_DEF)
    >>>>PsiElement(ELEMENT)
    >>>>PsiWhiteSpace
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiElement(OPEN_CURLY)
    >>>>Element(ELEMENT_BODY)
    >>>>>Element(ELEMENT_BODY_LINE)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(ELEMENT_BODY_LINE)
    >>>>>>PsiElement(TYPE)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>Element(STRING)
    >>>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>>>>PsiElement(STRING_VALUE)
    >>>>>>>PsiElement(DOUBLE_QUOTE)
    >>>>>>PsiElement(EOL)
    >>>>>PsiWhiteSpace
    >>>>>Element(ELEMENT_BODY_LINE)
    >>>>>>PsiElement(DOCREF)
    >>>>>>PsiElement(COLON)
    >>>>>>PsiWhiteSpace
    >>>>>>PsiElement(LABEL)
    >>>>>>PsiElement(EOL)
    >>>>PsiWhiteSpace
    >>>>PsiElement(CLOSE_CURLY)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(REQUIREMENT_LINE)
    >>>Element(RELATIONSHIP_DEF)
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiElement(REQ_LINE)
    >>>>PsiWhiteSpace
    >>>>Element(REQ_RELATIONSHIP)
    >>>>>PsiElement(SATISFIES)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ARROW_RIGHT)
    >>>>PsiWhiteSpace
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(REQUIREMENT_LINE)
    >>>Element(RELATIONSHIP_DEF)
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiElement(REQ_LINE)
    >>>>PsiWhiteSpace
    >>>>Element(REQ_RELATIONSHIP)
    >>>>>PsiElement(TRACES)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ARROW_RIGHT)
    >>>>PsiWhiteSpace
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(REQUIREMENT_LINE)
    >>>Element(RELATIONSHIP_DEF)
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiElement(REQ_LINE)
    >>>>PsiWhiteSpace
    >>>>Element(REQ_RELATIONSHIP)
    >>>>>PsiElement(CONTAINS)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ARROW_RIGHT)
    >>>>PsiWhiteSpace
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(REQUIREMENT_LINE)
    >>>Element(RELATIONSHIP_DEF)
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiElement(REQ_LINE)
    >>>>PsiWhiteSpace
    >>>>Element(REQ_RELATIONSHIP)
    >>>>>PsiElement(CONTAINS)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ARROW_RIGHT)
    >>>>PsiWhiteSpace
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(REQUIREMENT_LINE)
    >>>Element(RELATIONSHIP_DEF)
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiElement(REQ_LINE)
    >>>>PsiWhiteSpace
    >>>>Element(REQ_RELATIONSHIP)
    >>>>>PsiElement(DERIVES)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ARROW_RIGHT)
    >>>>PsiWhiteSpace
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(REQUIREMENT_LINE)
    >>>Element(RELATIONSHIP_DEF)
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiElement(REQ_LINE)
    >>>>PsiWhiteSpace
    >>>>Element(REQ_RELATIONSHIP)
    >>>>>PsiElement(REFINES)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ARROW_RIGHT)
    >>>>PsiWhiteSpace
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(REQUIREMENT_LINE)
    >>>Element(RELATIONSHIP_DEF)
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiElement(REQ_LINE)
    >>>>PsiWhiteSpace
    >>>>Element(REQ_RELATIONSHIP)
    >>>>>PsiElement(VERIFIES)
    >>>>PsiWhiteSpace
    >>>>PsiElement(ARROW_RIGHT)
    >>>>PsiWhiteSpace
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>PsiElement(EOL)
    >>PsiWhiteSpace
    >>Element(REQUIREMENT_LINE)
    >>>Element(RELATIONSHIP_DEF)
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    >>>>PsiElement(ARROW_LEFT)
    >>>>PsiWhiteSpace
    >>>>Element(REQ_RELATIONSHIP)
    >>>>>PsiElement(COPIES)
    >>>>PsiWhiteSpace
    >>>>PsiElement(REQ_LINE)
    >>>>PsiWhiteSpace
    >>>>Element(IDENTIFIER)
    >>>>>PsiElement(ID)
    """.trimIndent()
    doTest(content, expectedTree)
  }
}
