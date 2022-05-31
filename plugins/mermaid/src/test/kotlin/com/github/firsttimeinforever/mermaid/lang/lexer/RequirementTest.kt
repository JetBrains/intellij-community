package com.github.firsttimeinforever.mermaid.lang.lexer

import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.CLOSE_CURLY
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.COLON
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.DOUBLE_QUOTE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.EOL
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.ID
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.LABEL
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.OPEN_CURLY
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement
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
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.ID_KEYWORD
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.INSPECTION
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.INTERFACE_REQUIREMENT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.LOW
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.MEDIUM
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.PERFORMANCE_REQUIREMENT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.PHYSICAL_REQUIREMENT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.REFINES
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.REQUIREMENT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.REQUIREMENT_DIAGRAM
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.RISK
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.SATISFIES
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.TEST
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.TEXT
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.TRACES
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.TYPE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.VERIFIES
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Requirement.VERIFY_METHOD
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.STRING_VALUE
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.WHITE_SPACE

class RequirementTest : MermaidLexerTestCase() {
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
    val expected = listOf(
      Token(REQUIREMENT_DIAGRAM, 0, 18, "requirementDiagram"),
      Token(EOL, 18, 19, "\n"),
      Token(WHITE_SPACE, 19, 21, "  "),
      Token(REQUIREMENT, 21, 32, "requirement"),
      Token(WHITE_SPACE, 32, 33, " "),
      Token(ID, 33, 42, "test_req "),
      Token(OPEN_CURLY, 42, 43, "{"),
      Token(EOL, 43, 44, "\n"),
      Token(WHITE_SPACE, 44, 48, "    "),
      Token(ID_KEYWORD, 48, 50, "id"),
      Token(COLON, 50, 51, ":"),
      Token(WHITE_SPACE, 51, 52, " "),
      Token(LABEL, 52, 53, "1"),
      Token(EOL, 53, 54, "\n"),
      Token(WHITE_SPACE, 54, 58, "    "),
      Token(TEXT, 58, 62, "text"),
      Token(COLON, 62, 63, ":"),
      Token(WHITE_SPACE, 63, 64, " "),
      Token(LABEL, 64, 78, "the test text."),
      Token(EOL, 78, 79, "\n"),
      Token(WHITE_SPACE, 79, 83, "    "),
      Token(RISK, 83, 87, "risk"),
      Token(COLON, 87, 88, ":"),
      Token(WHITE_SPACE, 88, 89, " "),
      Token(HIGH, 89, 93, "high"),
      Token(EOL, 93, 94, "\n"),
      Token(WHITE_SPACE, 94, 98, "    "),
      Token(VERIFY_METHOD, 98, 110, "verifymethod"),
      Token(COLON, 110, 111, ":"),
      Token(WHITE_SPACE, 111, 112, " "),
      Token(TEST, 112, 116, "test"),
      Token(EOL, 116, 117, "\n"),
      Token(WHITE_SPACE, 117, 119, "  "),
      Token(CLOSE_CURLY, 119, 120, "}"),
      Token(EOL, 120, 121, "\n"),
      Token(WHITE_SPACE, 121, 123, "  "),
      Token(ELEMENT, 123, 130, "element"),
      Token(WHITE_SPACE, 130, 131, " "),
      Token(ID, 131, 143, "test_entity "),
      Token(OPEN_CURLY, 143, 144, "{"),
      Token(EOL, 144, 145, "\n"),
      Token(WHITE_SPACE, 145, 149, "    "),
      Token(TYPE, 149, 153, "type"),
      Token(COLON, 153, 154, ":"),
      Token(WHITE_SPACE, 154, 155, " "),
      Token(LABEL, 155, 165, "simulation"),
      Token(EOL, 165, 166, "\n"),
      Token(WHITE_SPACE, 166, 168, "  "),
      Token(CLOSE_CURLY, 168, 169, "}"),
      Token(EOL, 169, 171, "\n"),
      Token(WHITE_SPACE, 171, 173, "  "),
      Token(ID, 173, 185, "test_entity "),
      Token(Requirement.REQ_LINE, 185, 186, "-"),
      Token(WHITE_SPACE, 186, 187, " "),
      Token(SATISFIES, 187, 196, "satisfies"),
      Token(WHITE_SPACE, 196, 197, " "),
      Token(ARROW_RIGHT, 197, 199, "->"),
      Token(WHITE_SPACE, 199, 200, " "),
      Token(ID, 200, 208, "test_req")
    )
    doTest(content, expected)
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
    val expected = listOf(
      Token(REQUIREMENT_DIAGRAM, 0, 18, "requirementDiagram"),
      Token(EOL, 18, 19, "\n"),
      Token(WHITE_SPACE, 19, 21, "  "),
      Token(REQUIREMENT, 21, 32, "requirement"),
      Token(WHITE_SPACE, 32, 33, " "),
      Token(ID, 33, 42, "test_req "),
      Token(OPEN_CURLY, 42, 43, "{"),
      Token(EOL, 43, 44, "\n"),
      Token(WHITE_SPACE, 44, 48, "    "),
      Token(ID_KEYWORD, 48, 50, "id"),
      Token(COLON, 50, 51, ":"),
      Token(WHITE_SPACE, 51, 52, " "),
      Token(LABEL, 52, 53, "1"),
      Token(EOL, 53, 54, "\n"),
      Token(WHITE_SPACE, 54, 58, "    "),
      Token(TEXT, 58, 62, "text"),
      Token(COLON, 62, 63, ":"),
      Token(WHITE_SPACE, 63, 64, " "),
      Token(LABEL, 64, 78, "the test text."),
      Token(EOL, 78, 79, "\n"),
      Token(WHITE_SPACE, 79, 83, "    "),
      Token(RISK, 83, 87, "risk"),
      Token(COLON, 87, 88, ":"),
      Token(WHITE_SPACE, 88, 89, " "),
      Token(HIGH, 89, 93, "high"),
      Token(EOL, 93, 94, "\n"),
      Token(WHITE_SPACE, 94, 98, "    "),
      Token(VERIFY_METHOD, 98, 110, "verifymethod"),
      Token(COLON, 110, 111, ":"),
      Token(WHITE_SPACE, 111, 112, " "),
      Token(TEST, 112, 116, "test"),
      Token(EOL, 116, 117, "\n"),
      Token(WHITE_SPACE, 117, 119, "  "),
      Token(CLOSE_CURLY, 119, 120, "}"),
      Token(EOL, 120, 122, "\n"),
      Token(WHITE_SPACE, 122, 124, "  "),
      Token(FUNCTIONAL_REQUIREMENT, 124, 145, "functionalRequirement"),
      Token(WHITE_SPACE, 145, 146, " "),
      Token(ID, 146, 156, "test_req2 "),
      Token(OPEN_CURLY, 156, 157, "{"),
      Token(EOL, 157, 158, "\n"),
      Token(WHITE_SPACE, 158, 162, "    "),
      Token(ID_KEYWORD, 162, 164, "id"),
      Token(COLON, 164, 165, ":"),
      Token(WHITE_SPACE, 165, 166, " "),
      Token(LABEL, 166, 169, "1.1"),
      Token(EOL, 169, 170, "\n"),
      Token(WHITE_SPACE, 170, 174, "    "),
      Token(TEXT, 174, 178, "text"),
      Token(COLON, 178, 179, ":"),
      Token(WHITE_SPACE, 179, 180, " "),
      Token(LABEL, 180, 201, "the second test text."),
      Token(EOL, 201, 202, "\n"),
      Token(WHITE_SPACE, 202, 206, "    "),
      Token(RISK, 206, 210, "risk"),
      Token(COLON, 210, 211, ":"),
      Token(WHITE_SPACE, 211, 212, " "),
      Token(LOW, 212, 215, "low"),
      Token(EOL, 215, 216, "\n"),
      Token(WHITE_SPACE, 216, 220, "    "),
      Token(VERIFY_METHOD, 220, 232, "verifymethod"),
      Token(COLON, 232, 233, ":"),
      Token(WHITE_SPACE, 233, 234, " "),
      Token(INSPECTION, 234, 244, "inspection"),
      Token(EOL, 244, 245, "\n"),
      Token(WHITE_SPACE, 245, 247, "  "),
      Token(CLOSE_CURLY, 247, 248, "}"),
      Token(EOL, 248, 250, "\n"),
      Token(WHITE_SPACE, 250, 252, "  "),
      Token(PERFORMANCE_REQUIREMENT, 252, 274, "performanceRequirement"),
      Token(WHITE_SPACE, 274, 275, " "),
      Token(ID, 275, 285, "test_req3 "),
      Token(OPEN_CURLY, 285, 286, "{"),
      Token(EOL, 286, 287, "\n"),
      Token(WHITE_SPACE, 287, 291, "    "),
      Token(ID_KEYWORD, 291, 293, "id"),
      Token(COLON, 293, 294, ":"),
      Token(WHITE_SPACE, 294, 295, " "),
      Token(LABEL, 295, 298, "1.2"),
      Token(EOL, 298, 299, "\n"),
      Token(WHITE_SPACE, 299, 303, "    "),
      Token(TEXT, 303, 307, "text"),
      Token(COLON, 307, 308, ":"),
      Token(WHITE_SPACE, 308, 309, " "),
      Token(LABEL, 309, 329, "the third test text."),
      Token(EOL, 329, 330, "\n"),
      Token(WHITE_SPACE, 330, 334, "    "),
      Token(RISK, 334, 338, "risk"),
      Token(COLON, 338, 339, ":"),
      Token(WHITE_SPACE, 339, 340, " "),
      Token(MEDIUM, 340, 346, "medium"),
      Token(EOL, 346, 347, "\n"),
      Token(WHITE_SPACE, 347, 351, "    "),
      Token(VERIFY_METHOD, 351, 363, "verifymethod"),
      Token(COLON, 363, 364, ":"),
      Token(WHITE_SPACE, 364, 365, " "),
      Token(DEMONSTRATION, 365, 378, "demonstration"),
      Token(EOL, 378, 379, "\n"),
      Token(WHITE_SPACE, 379, 381, "  "),
      Token(CLOSE_CURLY, 381, 382, "}"),
      Token(EOL, 382, 384, "\n"),
      Token(WHITE_SPACE, 384, 386, "  "),
      Token(INTERFACE_REQUIREMENT, 386, 406, "interfaceRequirement"),
      Token(WHITE_SPACE, 406, 407, " "),
      Token(ID, 407, 417, "test_req4 "),
      Token(OPEN_CURLY, 417, 418, "{"),
      Token(EOL, 418, 419, "\n"),
      Token(WHITE_SPACE, 419, 423, "    "),
      Token(ID_KEYWORD, 423, 425, "id"),
      Token(COLON, 425, 426, ":"),
      Token(WHITE_SPACE, 426, 427, " "),
      Token(LABEL, 427, 432, "1.2.1"),
      Token(EOL, 432, 433, "\n"),
      Token(WHITE_SPACE, 433, 437, "    "),
      Token(TEXT, 437, 441, "text"),
      Token(COLON, 441, 442, ":"),
      Token(WHITE_SPACE, 442, 443, " "),
      Token(LABEL, 443, 464, "the fourth test text."),
      Token(EOL, 464, 465, "\n"),
      Token(WHITE_SPACE, 465, 469, "    "),
      Token(RISK, 469, 473, "risk"),
      Token(COLON, 473, 474, ":"),
      Token(WHITE_SPACE, 474, 475, " "),
      Token(MEDIUM, 475, 481, "medium"),
      Token(EOL, 481, 482, "\n"),
      Token(WHITE_SPACE, 482, 486, "    "),
      Token(VERIFY_METHOD, 486, 498, "verifymethod"),
      Token(COLON, 498, 499, ":"),
      Token(WHITE_SPACE, 499, 500, " "),
      Token(ANALYSIS, 500, 508, "analysis"),
      Token(EOL, 508, 509, "\n"),
      Token(WHITE_SPACE, 509, 511, "  "),
      Token(CLOSE_CURLY, 511, 512, "}"),
      Token(EOL, 512, 514, "\n"),
      Token(WHITE_SPACE, 514, 516, "  "),
      Token(PHYSICAL_REQUIREMENT, 516, 535, "physicalRequirement"),
      Token(WHITE_SPACE, 535, 536, " "),
      Token(ID, 536, 546, "test_req5 "),
      Token(OPEN_CURLY, 546, 547, "{"),
      Token(EOL, 547, 548, "\n"),
      Token(WHITE_SPACE, 548, 552, "    "),
      Token(ID_KEYWORD, 552, 554, "id"),
      Token(COLON, 554, 555, ":"),
      Token(WHITE_SPACE, 555, 556, " "),
      Token(LABEL, 556, 561, "1.2.2"),
      Token(EOL, 561, 562, "\n"),
      Token(WHITE_SPACE, 562, 566, "    "),
      Token(TEXT, 566, 570, "text"),
      Token(COLON, 570, 571, ":"),
      Token(WHITE_SPACE, 571, 572, " "),
      Token(LABEL, 572, 592, "the fifth test text."),
      Token(EOL, 592, 593, "\n"),
      Token(WHITE_SPACE, 593, 597, "    "),
      Token(RISK, 597, 601, "risk"),
      Token(COLON, 601, 602, ":"),
      Token(WHITE_SPACE, 602, 603, " "),
      Token(MEDIUM, 603, 609, "medium"),
      Token(EOL, 609, 610, "\n"),
      Token(WHITE_SPACE, 610, 614, "    "),
      Token(VERIFY_METHOD, 614, 626, "verifymethod"),
      Token(COLON, 626, 627, ":"),
      Token(WHITE_SPACE, 627, 628, " "),
      Token(ANALYSIS, 628, 636, "analysis"),
      Token(EOL, 636, 637, "\n"),
      Token(WHITE_SPACE, 637, 639, "  "),
      Token(CLOSE_CURLY, 639, 640, "}"),
      Token(EOL, 640, 642, "\n"),
      Token(WHITE_SPACE, 642, 644, "  "),
      Token(DESIGN_CONSTRAINT, 644, 660, "designConstraint"),
      Token(WHITE_SPACE, 660, 661, " "),
      Token(ID, 661, 671, "test_req6 "),
      Token(OPEN_CURLY, 671, 672, "{"),
      Token(EOL, 672, 673, "\n"),
      Token(WHITE_SPACE, 673, 677, "    "),
      Token(ID_KEYWORD, 677, 679, "id"),
      Token(COLON, 679, 680, ":"),
      Token(WHITE_SPACE, 680, 681, " "),
      Token(LABEL, 681, 686, "1.2.3"),
      Token(EOL, 686, 687, "\n"),
      Token(WHITE_SPACE, 687, 691, "    "),
      Token(TEXT, 691, 695, "text"),
      Token(COLON, 695, 696, ":"),
      Token(WHITE_SPACE, 696, 697, " "),
      Token(LABEL, 697, 717, "the sixth test text."),
      Token(EOL, 717, 718, "\n"),
      Token(WHITE_SPACE, 718, 722, "    "),
      Token(RISK, 722, 726, "risk"),
      Token(COLON, 726, 727, ":"),
      Token(WHITE_SPACE, 727, 728, " "),
      Token(MEDIUM, 728, 734, "medium"),
      Token(EOL, 734, 735, "\n"),
      Token(WHITE_SPACE, 735, 739, "    "),
      Token(VERIFY_METHOD, 739, 751, "verifymethod"),
      Token(COLON, 751, 752, ":"),
      Token(WHITE_SPACE, 752, 753, " "),
      Token(ANALYSIS, 753, 761, "analysis"),
      Token(EOL, 761, 762, "\n"),
      Token(WHITE_SPACE, 762, 764, "  "),
      Token(CLOSE_CURLY, 764, 765, "}"),
      Token(EOL, 765, 767, "\n"),
      Token(WHITE_SPACE, 767, 769, "  "),
      Token(ELEMENT, 769, 776, "element"),
      Token(WHITE_SPACE, 776, 777, " "),
      Token(ID, 777, 789, "test_entity "),
      Token(OPEN_CURLY, 789, 790, "{"),
      Token(EOL, 790, 791, "\n"),
      Token(WHITE_SPACE, 791, 795, "    "),
      Token(TYPE, 795, 799, "type"),
      Token(COLON, 799, 800, ":"),
      Token(WHITE_SPACE, 800, 801, " "),
      Token(LABEL, 801, 811, "simulation"),
      Token(EOL, 811, 812, "\n"),
      Token(WHITE_SPACE, 812, 814, "  "),
      Token(CLOSE_CURLY, 814, 815, "}"),
      Token(EOL, 815, 817, "\n"),
      Token(WHITE_SPACE, 817, 819, "  "),
      Token(ELEMENT, 819, 826, "element"),
      Token(WHITE_SPACE, 826, 827, " "),
      Token(ID, 827, 840, "test_entity2 "),
      Token(OPEN_CURLY, 840, 841, "{"),
      Token(EOL, 841, 842, "\n"),
      Token(WHITE_SPACE, 842, 846, "    "),
      Token(TYPE, 846, 850, "type"),
      Token(COLON, 850, 851, ":"),
      Token(WHITE_SPACE, 851, 852, " "),
      Token(LABEL, 852, 860, "word doc"),
      Token(EOL, 860, 861, "\n"),
      Token(WHITE_SPACE, 861, 865, "    "),
      Token(DOCREF, 865, 871, "docRef"),
      Token(COLON, 871, 872, ":"),
      Token(WHITE_SPACE, 872, 873, " "),
      Token(LABEL, 873, 889, "reqs/test_entity"),
      Token(EOL, 889, 890, "\n"),
      Token(WHITE_SPACE, 890, 892, "  "),
      Token(CLOSE_CURLY, 892, 893, "}"),
      Token(EOL, 893, 895, "\n"),
      Token(WHITE_SPACE, 895, 897, "  "),
      Token(ELEMENT, 897, 904, "element"),
      Token(WHITE_SPACE, 904, 905, " "),
      Token(ID, 905, 918, "test_entity3 "),
      Token(OPEN_CURLY, 918, 919, "{"),
      Token(EOL, 919, 920, "\n"),
      Token(WHITE_SPACE, 920, 924, "    "),
      Token(TYPE, 924, 928, "type"),
      Token(COLON, 928, 929, ":"),
      Token(WHITE_SPACE, 929, 930, " "),
      Token(DOUBLE_QUOTE, 930, 931, "\""),
      Token(STRING_VALUE, 931, 941, "test suite"),
      Token(DOUBLE_QUOTE, 941, 942, "\""),
      Token(EOL, 942, 943, "\n"),
      Token(WHITE_SPACE, 943, 947, "    "),
      Token(DOCREF, 947, 953, "docRef"),
      Token(COLON, 953, 954, ":"),
      Token(WHITE_SPACE, 954, 955, " "),
      Token(LABEL, 955, 979, "github.com/all_the_tests"),
      Token(EOL, 979, 980, "\n"),
      Token(WHITE_SPACE, 980, 982, "  "),
      Token(CLOSE_CURLY, 982, 983, "}"),
      Token(EOL, 983, 985, "\n"),
      Token(WHITE_SPACE, 985, 987, "  "),
      Token(ID, 987, 999, "test_entity "),
      Token(Requirement.REQ_LINE, 999, 1000, "-"),
      Token(WHITE_SPACE, 1000, 1001, " "),
      Token(SATISFIES, 1001, 1010, "satisfies"),
      Token(WHITE_SPACE, 1010, 1011, " "),
      Token(ARROW_RIGHT, 1011, 1013, "->"),
      Token(WHITE_SPACE, 1013, 1014, " "),
      Token(ID, 1014, 1023, "test_req2"),
      Token(EOL, 1023, 1024, "\n"),
      Token(WHITE_SPACE, 1024, 1026, "  "),
      Token(ID, 1026, 1035, "test_req "),
      Token(Requirement.REQ_LINE, 1035, 1036, "-"),
      Token(WHITE_SPACE, 1036, 1037, " "),
      Token(TRACES, 1037, 1043, "traces"),
      Token(WHITE_SPACE, 1043, 1044, " "),
      Token(ARROW_RIGHT, 1044, 1046, "->"),
      Token(WHITE_SPACE, 1046, 1047, " "),
      Token(ID, 1047, 1056, "test_req2"),
      Token(EOL, 1056, 1057, "\n"),
      Token(WHITE_SPACE, 1057, 1059, "  "),
      Token(ID, 1059, 1068, "test_req "),
      Token(Requirement.REQ_LINE, 1068, 1069, "-"),
      Token(WHITE_SPACE, 1069, 1070, " "),
      Token(CONTAINS, 1070, 1078, "contains"),
      Token(WHITE_SPACE, 1078, 1079, " "),
      Token(ARROW_RIGHT, 1079, 1081, "->"),
      Token(WHITE_SPACE, 1081, 1082, " "),
      Token(ID, 1082, 1091, "test_req3"),
      Token(EOL, 1091, 1092, "\n"),
      Token(WHITE_SPACE, 1092, 1094, "  "),
      Token(ID, 1094, 1104, "test_req3 "),
      Token(Requirement.REQ_LINE, 1104, 1105, "-"),
      Token(WHITE_SPACE, 1105, 1106, " "),
      Token(CONTAINS, 1106, 1114, "contains"),
      Token(WHITE_SPACE, 1114, 1115, " "),
      Token(ARROW_RIGHT, 1115, 1117, "->"),
      Token(WHITE_SPACE, 1117, 1118, " "),
      Token(ID, 1118, 1127, "test_req4"),
      Token(EOL, 1127, 1128, "\n"),
      Token(WHITE_SPACE, 1128, 1130, "  "),
      Token(ID, 1130, 1140, "test_req4 "),
      Token(Requirement.REQ_LINE, 1140, 1141, "-"),
      Token(WHITE_SPACE, 1141, 1142, " "),
      Token(DERIVES, 1142, 1149, "derives"),
      Token(WHITE_SPACE, 1149, 1150, " "),
      Token(ARROW_RIGHT, 1150, 1152, "->"),
      Token(WHITE_SPACE, 1152, 1153, " "),
      Token(ID, 1153, 1162, "test_req5"),
      Token(EOL, 1162, 1163, "\n"),
      Token(WHITE_SPACE, 1163, 1165, "  "),
      Token(ID, 1165, 1175, "test_req5 "),
      Token(Requirement.REQ_LINE, 1175, 1176, "-"),
      Token(WHITE_SPACE, 1176, 1177, " "),
      Token(REFINES, 1177, 1184, "refines"),
      Token(WHITE_SPACE, 1184, 1185, " "),
      Token(ARROW_RIGHT, 1185, 1187, "->"),
      Token(WHITE_SPACE, 1187, 1188, " "),
      Token(ID, 1188, 1197, "test_req6"),
      Token(EOL, 1197, 1198, "\n"),
      Token(WHITE_SPACE, 1198, 1200, "  "),
      Token(ID, 1200, 1213, "test_entity3 "),
      Token(Requirement.REQ_LINE, 1213, 1214, "-"),
      Token(WHITE_SPACE, 1214, 1215, " "),
      Token(VERIFIES, 1215, 1223, "verifies"),
      Token(WHITE_SPACE, 1223, 1224, " "),
      Token(ARROW_RIGHT, 1224, 1226, "->"),
      Token(WHITE_SPACE, 1226, 1227, " "),
      Token(ID, 1227, 1236, "test_req5"),
      Token(EOL, 1236, 1237, "\n"),
      Token(WHITE_SPACE, 1237, 1239, "  "),
      Token(ID, 1239, 1248, "test_req "),
      Token(ARROW_LEFT, 1248, 1250, "<-"),
      Token(WHITE_SPACE, 1250, 1251, " "),
      Token(COPIES, 1251, 1257, "copies"),
      Token(WHITE_SPACE, 1257, 1258, " "),
      Token(Requirement.REQ_LINE, 1258, 1259, "-"),
      Token(WHITE_SPACE, 1259, 1260, " "),
      Token(ID, 1260, 1272, "test_entity2")
    )
    doTest(content, expected)
  }
}
