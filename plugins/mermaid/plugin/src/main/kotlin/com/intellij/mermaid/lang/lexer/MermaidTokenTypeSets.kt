package com.intellij.mermaid.lang.lexer

import com.intellij.mermaid.lang.parser.MermaidElements
import com.intellij.psi.TokenType
import com.intellij.psi.tree.TokenSet

object MermaidTokenTypeSets {
  val WHITE_SPACES = TokenSet.create(MermaidTokens.WHITE_SPACE, MermaidTokens.EOL, TokenType.WHITE_SPACE)
  val WHITE_SPACES_WITHOUT_EOL = TokenSet.create(MermaidTokens.WHITE_SPACE, TokenType.WHITE_SPACE)

  val STATEMENTS = TokenSet.create(
    MermaidTokens.EOL,
    MermaidElements.PIE_STATEMENT,
    MermaidElements.JOURNEY_STATEMENT,
    MermaidElements.JOURNEY_SECTION_INNER_STATEMENT,
    MermaidElements.FLOWCHART_STATEMENT,
    MermaidElements.SEQUENCE_STATEMENT,
    MermaidElements.CLASS_DIAGRAM_STATEMENT,
    MermaidElements.CLASS_MEMBER_STATEMENT,
    MermaidElements.STATE_DIAGRAM_STATEMENT,
    MermaidElements.DIVIDER_STATEMENT,
    MermaidElements.SIMPLE_NOTE_CONTENT,
    MermaidElements.ER_STATEMENT,
    MermaidElements.ER_ATTRIBUTE,
    MermaidElements.GANTT_STATEMENT,
    MermaidElements.GANTT_SECTION_INNER_STATEMENT,
    MermaidElements.REQUIREMENT_STATEMENT,
    MermaidElements.REQUIREMENT_BODY_STATEMENT,
    MermaidElements.ELEMENT_BODY_STATEMENT,
    MermaidElements.GIT_GRAPH_STATEMENT,
    MermaidElements.C_4_STATEMENT,
    MermaidElements.MINDMAP_STATEMENT
  )

  val DIAGRAM_DOCUMENTS = TokenSet.create(
    MermaidElements.PIE_DOCUMENT,
    MermaidElements.JOURNEY_DOCUMENT,
    MermaidElements.JOURNEY_SECTION_DOCUMENT,
    MermaidElements.FLOWCHART_DOCUMENT,
    MermaidElements.SUBGRAPH_DOCUMENT,
    MermaidElements.SEQUENCE_DOCUMENT,
    MermaidElements.CLASS_DOCUMENT,
    MermaidElements.CLASS_MEMBERS_DOCUMENT,
    MermaidElements.STATE_DOCUMENT,
    MermaidElements.INNER_STATE_DOCUMENT,
    MermaidElements.COMPLEX_NOTE_CONTENT,
    MermaidElements.ER_DOCUMENT,
    MermaidElements.ER_ATTRIBUTES_DOCUMENT,
    MermaidElements.GANTT_DOCUMENT,
    MermaidElements.GANTT_SECTION_DOCUMENT,
    MermaidElements.REQUIREMENT_DOCUMENT,
    MermaidElements.REQUIREMENT_BODY,
    MermaidElements.ELEMENT_BODY,
    MermaidElements.GIT_GRAPH_DOCUMENT,
    MermaidElements.C_4_DOCUMENT,
    MermaidElements.BOUNDARY_BODY
  )

  val EXPAND_INDENT_AFTER = TokenSet.create(
    MermaidTokens.OPEN_CURLY,
    MermaidTokens.Pie.PIE,
    MermaidTokens.Pie.SHOW_DATA,
    MermaidTokens.Journey.JOURNEY,
    MermaidTokens.SECTION_TITLE,
    MermaidTokens.Flowchart.FLOWCHART,
    MermaidTokens.Flowchart.SUBGRAPH,
    MermaidTokens.Sequence.SEQUENCE,
    MermaidTokens.Sequence.LOOP,
    MermaidTokens.Sequence.ALT,
    MermaidTokens.Sequence.OPT,
    MermaidTokens.Sequence.PAR,
    MermaidTokens.Sequence.RECT,
    MermaidTokens.Sequence.CRITICAL,
    MermaidTokens.Sequence.ELSE,
    MermaidTokens.Sequence.AND,
    MermaidTokens.Sequence.OPTION,
    MermaidTokens.ClassDiagram.CLASS_DIAGRAM,
    MermaidTokens.StateDiagram.STATE_DIAGRAM,
    MermaidTokens.NOTE,
    MermaidTokens.EntityRelationship.ENTITY_RELATIONSHIP,
    MermaidTokens.Gantt.GANTT,
    MermaidTokens.Requirement.REQUIREMENT_DIAGRAM,
    MermaidTokens.GitGraph.GIT_GRAPH,
    MermaidTokens.C4.C4_CONTEXT,
    MermaidTokens.C4.C4_CONTAINER,
    MermaidTokens.C4.C4_COMPONENT,
    MermaidTokens.C4.C4_DYNAMIC,
    MermaidTokens.C4.C4_DEPLOYMENT
  )
}
