// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.lexer

import com.intellij.mermaid.lang.parser.MermaidElements
import com.intellij.psi.TokenType
import com.intellij.psi.tree.TokenSet

object MermaidTokenTypeSets {
  val WHITE_SPACES = TokenSet.create(MermaidTokens.WHITE_SPACE, MermaidTokens.EOL, TokenType.WHITE_SPACE)
  val WHITE_SPACES_WITHOUT_EOL = TokenSet.create(MermaidTokens.WHITE_SPACE, TokenType.WHITE_SPACE)

  //region STATEMENTS
  val STATEMENTS = TokenSet.create(
    //region GENERAL
    MermaidElements.DIRECTION_STATEMENT,
    MermaidElements.DIRECTIVE,
    MermaidElements.ACC_STATEMENT,
    MermaidElements.TITLE_STATEMENT,
    //endregion
    //region PIE
    MermaidElements.PIE_DATA_STATEMENT,
    //endregion
    //region JOURNEY
    MermaidElements.JOURNEY_DATA_STATEMENT,
    MermaidElements.JOURNEY_SECTION_STATEMENT,
    //endregion
    //region FLOWCHART
    MermaidElements.VERTEX_STATEMENT,
    MermaidElements.SUBGRAPH_STATEMENT,
    MermaidElements.STYLE_STATEMENT,
    MermaidElements.LINK_STYLE_STATEMENT,
    MermaidElements.CLASS_DEF_STATEMENT,
    MermaidElements.FLOWCHART_CLASS_STATEMENT,
    MermaidElements.FLOWCHART_CLICK_STATEMENT,
    //endregion
    //region SEQUENCE
    MermaidElements.ACTOR_STATEMENT,
    MermaidElements.SIGNAL_STATEMENT,
    MermaidElements.AUTONUMBER_STATEMENT,
    MermaidElements.ACTIVATE_STATEMENT,
    MermaidElements.DEACTIVATE_STATEMENT,
    MermaidElements.NOTE_STATEMENT,
    MermaidElements.LINKS_STATEMENT,
    MermaidElements.LINK_STATEMENT,
    MermaidElements.LOOP_STATEMENT,
    MermaidElements.RECT_STATEMENT,
    MermaidElements.OPT_STATEMENT,
    MermaidElements.BREAK_STATEMENT,
    MermaidElements.BOX_STATEMENT,
    MermaidElements.ALT_STATEMENT,
    MermaidElements.PAR_STATEMENT,
    MermaidElements.PAR_OVER_STATEMENT,
    MermaidElements.CRITICAL_STATEMENT,
    //endregion
    //region CLASS
    MermaidElements.CLASS_STATEMENT,
    MermaidElements.RELATION_STATEMENT,
    MermaidElements.NAMESPACE_STATEMENT,
    MermaidElements.MEMBER_STATEMENT,
    MermaidElements.ANNOTATION_STATEMENT,
    MermaidElements.CLASS_DIAGRAM_NOTE_STATEMENT,
    MermaidElements.CLASS_DIAGRAM_CLICK_STATEMENT,
    MermaidElements.ATTRIBUTE,
    MermaidElements.ANNOTATION,
    //endregion
    //region STATE
    MermaidElements.STATE_DECLARATION,
    MermaidElements.COMPOSITE_STATE_DECLARATION,
    MermaidElements.STATE_RELATION_STATEMENT,
    MermaidElements.STATE_ID,
    MermaidElements.STATE_NOTE,
    MermaidElements.STATE_CLASS_DEF_STATEMENT,
    MermaidElements.CSS_CLASS_STATEMENT,
    MermaidElements.DIVIDER_STATEMENT,
    //endregion
    //region ER
    MermaidElements.ER_RELATION_STATEMENT,
    MermaidElements.ENTITY_DECLARATION,
    MermaidElements.ER_IDENTIFIER,
    MermaidElements.ER_ATTRIBUTE,
    //endregion
    //region GANTT
    MermaidElements.GANTT_DATE_FORMAT_STATEMENT,
    MermaidElements.GANTT_EXCLUDES_STATEMENT,
    MermaidElements.GANTT_INCLUDES_STATEMENT,
    MermaidElements.GANTT_AXIS_FORMAT_STATEMENT,
    MermaidElements.GANTT_TODAY_MARKER_STATEMENT,
    MermaidElements.GANTT_TICK_INTERVAL_STATEMENT,
    MermaidElements.GANTT_SECTION_STATEMENT,
    MermaidElements.GANTT_DATA_STATEMENT,
    MermaidElements.GANTT_CLICK_STATEMENT,
    MermaidElements.GANTT_INCLUSIVE_END_DATES_STATEMENT,
    MermaidElements.GANTT_TOP_AXIS_STATEMENT,
    MermaidElements.GANTT_WEEKDAY_STATEMENT,
    //endregion
    //region REQUIREMENT
    MermaidElements.REQUIREMENT_DEF,
    MermaidElements.ELEMENT_DEF,
    MermaidElements.RELATIONSHIP_DEF,
    MermaidElements.REQUIREMENT_ID_ATTRIBUTE,
    MermaidElements.REQUIREMENT_TEXT_ATTRIBUTE,
    MermaidElements.REQUIREMENT_RISK_ATTRIBUTE,
    MermaidElements.REQUIREMENT_VERIFY_METHOD_ATTRIBUTE,
    MermaidElements.ELEMENT_TYPE_ATTRIBUTE,
    MermaidElements.ELEMENT_DOC_REF_ATTRIBUTE,
    //endregion
    //region GIT
    MermaidElements.COMMIT_STATEMENT,
    MermaidElements.MERGE_STATEMENT,
    MermaidElements.CHERRY_PICK_STATEMENT,
    MermaidElements.BRANCH_STATEMENT,
    MermaidElements.CHECKOUT_STATEMENT,
    //endregion
    //region C4
    MermaidElements.C_4_COMPONENT_STATEMENT,
    MermaidElements.BOUNDARY_STATEMENT,
    //endregion
    //region MINDMAP
//    MermaidElements.MINDMAP_NODE_STATEMENT,
//    MermaidElements.ICON_STATEMENT,
//    MermaidElements.MINDMAP_CLASS_STATEMENT,
    //endregion
    //region TIMELINE
    MermaidElements.TIMELINE_DATA_STATEMENT,
    MermaidElements.TIMELINE_SECTION_STATEMENT,
    //endregion
    //region QUADRANT
    MermaidElements.POINT_STATEMENT,
    MermaidElements.AXIS_DETAILS_STATEMENT,
    MermaidElements.QUADRANT_DETAILS_STATEMENT,
    //endregion
    //region SANKEY
    MermaidElements.SANKEY_RECORD_STATEMENT,
    //endregion
    //region XYCHART
    MermaidElements.X_AXIS_STATEMENT,
    MermaidElements.Y_AXIS_STATEMENT,
    MermaidElements.LINE_STATEMENT,
    MermaidElements.BAR_STATEMENT,
    //endregion
    //region BLOCK
    MermaidElements.BLOCK_DIAGRAM_NODE_STATEMENT,
    MermaidElements.COLUMNS_STATEMENT,
    MermaidElements.BLOCK_STATEMENT,
    MermaidElements.SPACE_STATEMENT,
    MermaidElements.BLOCK_DIAGRAM_COMPLEX_STATEMENT,
    //endregion
  )
  //endregion

  val MINDMAP_STATEMENTS = TokenSet.create(
    MermaidElements.MINDMAP_NODE_STATEMENT,
    MermaidElements.ICON_STATEMENT,
    MermaidElements.MINDMAP_CLASS_STATEMENT,
  )

  //region DIAGRAM_BODIES_AND_BLOCKS
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
    MermaidElements.NAMESPACE_BLOCK,
    MermaidElements.STATE_BODY,
    MermaidElements.STATE_BLOCK,
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
    MermaidElements.QUADRANT_BODY,
    MermaidElements.SANKEY_BODY,
    MermaidElements.XY_CHART_BODY,
    MermaidElements.BLOCK_DIAGRAM_BODY,
  )
  //endregion

  //region STRUCTURED_STATEMENTS
  val STRUCTURED_STATEMENTS = TokenSet.create(
    MermaidElements.JOURNEY_SECTION_STATEMENT,
    MermaidElements.SUBGRAPH_STATEMENT,
    //region SEQUENCE
    MermaidElements.LOOP_STATEMENT,
    MermaidElements.RECT_STATEMENT,
    MermaidElements.OPT_STATEMENT,
    MermaidElements.BREAK_STATEMENT,
    MermaidElements.BOX_STATEMENT,
    MermaidElements.ALT_STATEMENT,
    MermaidElements.PAR_STATEMENT,
    MermaidElements.PAR_OVER_STATEMENT,
    MermaidElements.CRITICAL_STATEMENT,
    //endregion
    //region CLASS
    MermaidElements.CLASS_STATEMENT,
    MermaidElements.NAMESPACE_STATEMENT,
    //endregion
    MermaidElements.COMPOSITE_STATE_DECLARATION,
    MermaidElements.ENTITY_DECLARATION,
    MermaidElements.GANTT_SECTION_STATEMENT,
    //region REQUIREMENT
    MermaidElements.REQUIREMENT_DEF,
    MermaidElements.ELEMENT_DEF,
    //endregion
    MermaidElements.BOUNDARY_STATEMENT,
    MermaidElements.TIMELINE_SECTION_STATEMENT,
    MermaidElements.BLOCK_STATEMENT,
  )
  //endregion

  //region EXPAND_INDENT_AFTER
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
    MermaidTokens.Sequence.PAR_OVER,
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
    MermaidTokens.Quadrant.QUADRANT_CHART,
    MermaidTokens.Sankey.SANKEY,
    MermaidTokens.XYChart.XY_CHART,
    MermaidTokens.XYChart.ORIENTATION_VALUE,
    MermaidTokens.Block.BLOCK_DIAGRAM,
    MermaidTokens.Block.BLOCK,
  )
  //endregion
}
