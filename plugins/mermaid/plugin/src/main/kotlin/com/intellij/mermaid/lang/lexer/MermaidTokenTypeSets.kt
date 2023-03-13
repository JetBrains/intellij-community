package com.intellij.mermaid.lang.lexer

import com.intellij.mermaid.lang.parser.MermaidElements
import com.intellij.psi.TokenType
import com.intellij.psi.tree.TokenSet

object MermaidTokenTypeSets {
  val WHITE_SPACES = TokenSet.create(MermaidTokens.WHITE_SPACE, MermaidTokens.EOL, TokenType.WHITE_SPACE)
  val WHITE_SPACES_WITHOUT_EOL = TokenSet.create(MermaidTokens.WHITE_SPACE, TokenType.WHITE_SPACE)

  val STATEMENTS = TokenSet.create(
    MermaidTokens.EOL,
    MermaidElements.DIRECTION_STATEMENT,
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
    MermaidElements.REQUIREMENT_BLOCK_STATEMENT,
    MermaidElements.ELEMENT_BLOCK_STATEMENT,
    MermaidElements.GIT_GRAPH_STATEMENT,
    MermaidElements.C_4_STATEMENT,
    MermaidElements.MINDMAP_STATEMENT,
    MermaidElements.TIMELINE_STATEMENT,
    MermaidElements.TIMELINE_SECTION_INNER_STATEMENT
  )

  val DIAGRAM_BODIES_AND_BLOCKS = TokenSet.create(
    MermaidElements.PIE_BODY,
    MermaidElements.JOURNEY_BODY,
    MermaidElements.JOURNEY_SECTION_BLOCK,
    MermaidElements.FLOWCHART_BODY,
    MermaidElements.SUBGRAPH_BLOCK,
    MermaidElements.SEQUENCE_BODY,
    MermaidElements.BOX_BLOCK,
    MermaidElements.CLASS_BODY,
    MermaidElements.CLASS_BLOCK,
    MermaidElements.STATE_BODY,
    MermaidElements.STATE_BLOCK,
    MermaidElements.COMPLEX_NOTE_CONTENT,
    MermaidElements.ER_BODY,
    MermaidElements.ER_ENTITY_BLOCK,
    MermaidElements.GANTT_BODY,
    MermaidElements.GANTT_SECTION_BLOCK,
    MermaidElements.REQUIREMENT_DIAGRAM_BODY,
    MermaidElements.REQUIREMENT_BLOCK,
    MermaidElements.ELEMENT_BLOCK,
    MermaidElements.GIT_GRAPH_BODY,
    MermaidElements.C_4_BODY,
    MermaidElements.BOUNDARY_BLOCK,
    MermaidElements.TIMELINE_BODY,
    MermaidElements.TIMELINE_SECTION_BLOCK,
  )

  val EXPAND_INDENT_AFTER = TokenSet.create(
    MermaidTokens.OPEN_CURLY,
    MermaidTokens.Pie.PIE,
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
    MermaidTokens.Sequence.BOX,
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
    MermaidTokens.C4.C4_DEPLOYMENT,
    MermaidTokens.Timeline.TIMELINE,
  )
}
