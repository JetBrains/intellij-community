// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.highlighting

import com.intellij.lexer.Lexer
import com.intellij.mermaid.lang.lexer.MermaidLexer
import com.intellij.mermaid.lang.lexer.MermaidTokens
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

class MermaidHighlighter : SyntaxHighlighterBase() {
  override fun getHighlightingLexer(): Lexer {
    return MermaidLexer()
  }

  override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
    val highlight = highlights[tokenType] ?: HighlighterColors.TEXT
    return arrayOf(highlight)
  }

  companion object {
    private val highlights by lazy { createHighlights() }

    private fun createHighlights(): Map<IElementType, TextAttributesKey> {
      val holder = hashMapOf<IElementType, TextAttributesKey>()
      addBaseHighlights(holder)
      addPieHighlights(holder)
      addC4Highlights(holder)
      addFlowchartHighlights(holder)
      addMindmapHighlights(holder)
      addClassDiagramHighlights(holder)
      addEntityRelationshipHighlights(holder)
      addGanttDiagramHighlights(holder)
      addGitGraphHighlights(holder)
      addJourneyHighlights(holder)
      addRequirementDiagramHighlights(holder)
      addStateDiagramHighlights(holder)
      addSequenceHighlights(holder)
      addTimelineHighlights(holder)
      addQuadrantHighlights(holder)
      addSankeyHighlights(holder)
      addXYChartHighlights(holder)
      addBlockHighlights(holder)
      return holder
    }

    //region Details
    private fun addPieHighlights(holder: MutableMap<IElementType, TextAttributesKey>) {
      holder[MermaidTokens.Pie.PIE] = MermaidTextAttributes.diagram_name
      holder[MermaidTokens.Pie.SHOW_DATA] = MermaidTextAttributes.keyword
    }

    private fun addJourneyHighlights(holder: MutableMap<IElementType, TextAttributesKey>) {
      holder[MermaidTokens.Journey.JOURNEY] = MermaidTextAttributes.diagram_name
    }

    private fun addFlowchartHighlights(holder: MutableMap<IElementType, TextAttributesKey>) {
      holder[MermaidTokens.Flowchart.FLOWCHART] = MermaidTextAttributes.diagram_name
      fillMap(
        holder,
        MermaidTextAttributes.keyword,
        MermaidTokens.Flowchart.SUBGRAPH,
        MermaidTokens.CLASS_DEF
      )
      holder[MermaidTokens.Flowchart.LINK_TEXT] = MermaidTextAttributes.note
    }

    private fun addSequenceHighlights(holder: MutableMap<IElementType, TextAttributesKey>) {
      holder[MermaidTokens.Sequence.SEQUENCE] = MermaidTextAttributes.diagram_name
      fillMap(
        holder,
        MermaidTextAttributes.keyword,
        MermaidTokens.Sequence.PARTICIPANT,
        MermaidTokens.Sequence.ACTOR,
        MermaidTokens.Sequence.CREATE,
        MermaidTokens.Sequence.DESTROY,
        MermaidTokens.Sequence.ACTIVATE,
        MermaidTokens.Sequence.DEACTIVATE,
        MermaidTokens.Sequence.OVER,
        MermaidTokens.Sequence.LOOP,
        MermaidTokens.Sequence.ALT,
        MermaidTokens.Sequence.ELSE,
        MermaidTokens.Sequence.OPT,
        MermaidTokens.Sequence.PAR,
        MermaidTokens.Sequence.PAR_OVER,
        MermaidTokens.Sequence.AND,
        MermaidTokens.Sequence.RECT,
        MermaidTokens.Sequence.CRITICAL,
        MermaidTokens.Sequence.OPTION,
        MermaidTokens.Sequence.BREAK,
        MermaidTokens.Sequence.AUTONUMBER,
        MermaidTokens.Sequence.OFF,
        MermaidTokens.Sequence.LINKS,
        MermaidTokens.Sequence.BOX,
      )
      fillMap(
        holder,
        MermaidTextAttributes.edge,
        MermaidTokens.Sequence.SOLID_ARROW,
        MermaidTokens.Sequence.DOTTED_ARROW,
        MermaidTokens.Sequence.SOLID_OPEN_ARROW,
        MermaidTokens.Sequence.DOTTED_OPEN_ARROW,
        MermaidTokens.Sequence.SOLID_CROSS,
        MermaidTokens.Sequence.DOTTED_CROSS,
        MermaidTokens.Sequence.SOLID_POINT,
        MermaidTokens.Sequence.DOTTED_POINT
      )
      holder[MermaidTokens.Sequence.MESSAGE] = MermaidTextAttributes.note
    }

    private fun addClassDiagramHighlights(holder: MutableMap<IElementType, TextAttributesKey>) {
      holder[MermaidTokens.ClassDiagram.CLASS_DIAGRAM] = MermaidTextAttributes.diagram_name
      holder[MermaidTokens.ClassDiagram.CLASS_ID] = MermaidTextAttributes.identifier
      fillMap(
        holder,
        MermaidTextAttributes.keyword,
        MermaidTokens.ClassDiagram.NOTE_FOR,
        MermaidTokens.ClassDiagram.NAMESPACE
      )

      fillMap(
        holder,
        MermaidTextAttributes.edge,
        MermaidTokens.ClassDiagram.EXTENSION_START,
        MermaidTokens.ClassDiagram.EXTENSION_END,
        MermaidTokens.ClassDiagram.DEPENDENCY_START,
        MermaidTokens.ClassDiagram.DEPENDENCY_END,
        MermaidTokens.ClassDiagram.COMPOSITION,
        MermaidTokens.ClassDiagram.AGGREGATION,
        MermaidTokens.ClassDiagram.LOLLIPOP,
        MermaidTokens.ClassDiagram.LINE,
        MermaidTokens.ClassDiagram.DOTTED_LINE
      )
    }

    private fun addStateDiagramHighlights(holder: MutableMap<IElementType, TextAttributesKey>) {
      holder[MermaidTokens.StateDiagram.STATE_DIAGRAM] = MermaidTextAttributes.diagram_name
      holder[MermaidTokens.StateDiagram.STATE] = MermaidTextAttributes.keyword
    }

    private fun addEntityRelationshipHighlights(holder: MutableMap<IElementType, TextAttributesKey>) {
      holder[MermaidTokens.EntityRelationship.ENTITY_RELATIONSHIP] = MermaidTextAttributes.diagram_name
      fillMap(
        holder,
        MermaidTextAttributes.edge,
        MermaidTokens.EntityRelationship.ZERO_OR_ONE,
        MermaidTokens.EntityRelationship.ONE_OR_MORE,
        MermaidTokens.EntityRelationship.ZERO_OR_MORE,
        MermaidTokens.EntityRelationship.ONLY_ONE,
        MermaidTokens.EntityRelationship.MD_PARENT,
        MermaidTokens.EntityRelationship.IDENTIFYING,
        MermaidTokens.EntityRelationship.NON_IDENTIFYING
      )
      holder[MermaidTokens.EntityRelationship.ATTR_KEY] = MermaidTextAttributes.constant
    }

    private fun addGanttDiagramHighlights(holder: MutableMap<IElementType, TextAttributesKey>) {
      holder[MermaidTokens.Gantt.GANTT] = MermaidTextAttributes.diagram_name
      fillMap(
        holder,
        MermaidTextAttributes.keyword,
        MermaidTokens.Gantt.AXIS_FORMAT,
        MermaidTokens.Gantt.DATE_FORMAT,
        MermaidTokens.Gantt.EXCLUDES,
        MermaidTokens.Gantt.INCLUDES,
        MermaidTokens.Gantt.TICK_INTERVAL,
        MermaidTokens.Gantt.TODAY_MARKER,
        MermaidTokens.Gantt.WEEKDAY
      )
      fillMap(
        holder,
        MermaidTextAttributes.constant,
        MermaidTokens.Gantt.MONDAY,
        MermaidTokens.Gantt.TUESDAY,
        MermaidTokens.Gantt.WEDNESDAY,
        MermaidTokens.Gantt.THURSDAY,
        MermaidTokens.Gantt.FRIDAY,
        MermaidTokens.Gantt.SATURDAY,
        MermaidTokens.Gantt.SUNDAY
      )
    }

    private fun addRequirementDiagramHighlights(holder: MutableMap<IElementType, TextAttributesKey>) {
      holder[MermaidTokens.Requirement.REQUIREMENT_DIAGRAM] = MermaidTextAttributes.diagram_name
      fillMap(
        holder,
        MermaidTextAttributes.keyword,
        MermaidTokens.Requirement.REQUIREMENT,
        MermaidTokens.Requirement.FUNCTIONAL_REQUIREMENT,
        MermaidTokens.Requirement.INTERFACE_REQUIREMENT,
        MermaidTokens.Requirement.PERFORMANCE_REQUIREMENT,
        MermaidTokens.Requirement.PHYSICAL_REQUIREMENT,
        MermaidTokens.Requirement.DESIGN_CONSTRAINT,
        MermaidTokens.Requirement.ELEMENT,
        MermaidTokens.Requirement.TEXT,
        MermaidTokens.Requirement.RISK,
        MermaidTokens.Requirement.VERIFY_METHOD,
        MermaidTokens.Requirement.DOCREF
      )
      fillMap(
        holder,
        MermaidTextAttributes.constant,
        MermaidTokens.Requirement.LOW,
        MermaidTokens.Requirement.MEDIUM,
        MermaidTokens.Requirement.HIGH,
        MermaidTokens.Requirement.ANALYSIS,
        MermaidTokens.Requirement.INSPECTION,
        MermaidTokens.Requirement.TEST,
        MermaidTokens.Requirement.DEMONSTRATION,
        MermaidTokens.Requirement.CONTAINS,
        MermaidTokens.Requirement.COPIES,
        MermaidTokens.Requirement.DERIVES,
        MermaidTokens.Requirement.SATISFIES,
        MermaidTokens.Requirement.VERIFIES,
        MermaidTokens.Requirement.REFINES,
        MermaidTokens.Requirement.TRACES
      )
      fillMap(
        holder,
        MermaidTextAttributes.edge,
        MermaidTokens.Requirement.ARROW_LEFT,
        MermaidTokens.Requirement.ARROW_RIGHT,
        MermaidTokens.Requirement.REQ_LINE
      )
    }

    private fun addGitGraphHighlights(holder: MutableMap<IElementType, TextAttributesKey>) {
      holder[MermaidTokens.GitGraph.GIT_GRAPH] = MermaidTextAttributes.diagram_name
      fillMap(
        holder,
        MermaidTextAttributes.keyword,
        MermaidTokens.GitGraph.COMMIT,
        MermaidTokens.GitGraph.BRANCH,
        MermaidTokens.GitGraph.CHECKOUT,
        MermaidTokens.GitGraph.MERGE,
        MermaidTokens.GitGraph.TAG,
        MermaidTokens.GitGraph.MSG,
        MermaidTokens.GitGraph.CHERRY_PICK,
        MermaidTokens.GitGraph.ORDER,
        MermaidTokens.GitGraph.PARENT
      )
      fillMap(
        holder,
        MermaidTextAttributes.constant,
        MermaidTokens.GitGraph.NORMAL,
        MermaidTokens.GitGraph.REVERSE,
        MermaidTokens.GitGraph.HIGHLIGHT
      )
    }

    private fun addC4Highlights(holder: MutableMap<IElementType, TextAttributesKey>) {
      fillMap(
        holder,
        MermaidTextAttributes.diagram_name,
        MermaidTokens.C4.C4_CONTEXT,
        MermaidTokens.C4.C4_CONTAINER,
        MermaidTokens.C4.C4_COMPONENT,
        MermaidTokens.C4.C4_DYNAMIC,
        MermaidTokens.C4.C4_DEPLOYMENT
      )
      fillMap(
        holder,
        MermaidTextAttributes.keyword,
        MermaidTokens.C4.PERSON_EXT,
        MermaidTokens.C4.PERSON,
        MermaidTokens.C4.SYSTEM_EXT_QUEUE,
        MermaidTokens.C4.SYSTEM_EXT_DB,
        MermaidTokens.C4.SYSTEM_EXT,
        MermaidTokens.C4.SYSTEM_QUEUE,
        MermaidTokens.C4.SYSTEM_DB,
        MermaidTokens.C4.SYSTEM,
        MermaidTokens.C4.BOUNDARY,
        MermaidTokens.C4.ENTERPRISE_BOUNDARY,
        MermaidTokens.C4.SYSTEM_BOUNDARY,
        MermaidTokens.C4.CONTAINER_EXT_QUEUE,
        MermaidTokens.C4.CONTAINER_EXT_DB,
        MermaidTokens.C4.CONTAINER_EXT,
        MermaidTokens.C4.CONTAINER_QUEUE,
        MermaidTokens.C4.CONTAINER_DB,
        MermaidTokens.C4.CONTAINER,
        MermaidTokens.C4.CONTAINER_BOUNDARY,
        MermaidTokens.C4.COMPONENT_EXT_QUEUE,
        MermaidTokens.C4.COMPONENT_EXT_DB,
        MermaidTokens.C4.COMPONENT_EXT,
        MermaidTokens.C4.COMPONENT_QUEUE,
        MermaidTokens.C4.COMPONENT_DB,
        MermaidTokens.C4.COMPONENT,
        MermaidTokens.C4.NODE,
        MermaidTokens.C4.NODE_L,
        MermaidTokens.C4.NODE_R,
        MermaidTokens.C4.REL,
        MermaidTokens.C4.BIREL,
        MermaidTokens.C4.REL_U,
        MermaidTokens.C4.REL_D,
        MermaidTokens.C4.REL_L,
        MermaidTokens.C4.REL_R,
        MermaidTokens.C4.REL_B,
        MermaidTokens.C4.REL_INDEX,
        MermaidTokens.C4.UPDATE_EL_STYLE,
        MermaidTokens.C4.UPDATE_REL_STYLE,
        MermaidTokens.C4.UPDATE_LAYOUT_CONFIG
      )
      holder[MermaidTokens.C4.C4_ATTRIBUTE] = MermaidTextAttributes.identifier
      holder[MermaidTokens.C4.EQUALITY] = MermaidTextAttributes.operator
    }

    private fun addMindmapHighlights(holder: MutableMap<IElementType, TextAttributesKey>) {
      holder[MermaidTokens.Mindmap.MINDMAP] = MermaidTextAttributes.diagram_name
      fillMap(
        holder,
        MermaidTextAttributes.string,
        MermaidTokens.Mindmap.NODE_DESCR,
        MermaidTokens.Mindmap.ICON_VALUE
      )
      fillMap(
        holder,
        MermaidTextAttributes.operator,
        MermaidTokens.Mindmap.OPEN_ICON,
        MermaidTokens.Mindmap.CLOSE_ICON
      )
    }

    private fun addTimelineHighlights(holder: MutableMap<IElementType, TextAttributesKey>) {
      holder[MermaidTokens.Timeline.TIMELINE] = MermaidTextAttributes.diagram_name
    }

    private fun addQuadrantHighlights(holder: MutableMap<IElementType, TextAttributesKey>) {
      holder[MermaidTokens.Quadrant.QUADRANT_CHART] = MermaidTextAttributes.diagram_name
      fillMap(
        holder,
        MermaidTextAttributes.keyword,
        MermaidTokens.Quadrant.QUADRANT
      )
    }

    private fun addSankeyHighlights(holder: MutableMap<IElementType, TextAttributesKey>) {
      holder[MermaidTokens.Sankey.SANKEY] = MermaidTextAttributes.diagram_name
      fillMap(
        holder,
        MermaidTextAttributes.string,
        MermaidTokens.Sankey.SANKEY_TEXT
      )
    }

    private fun addXYChartHighlights(holder: MutableMap<IElementType, TextAttributesKey>) {
      holder[MermaidTokens.XYChart.XY_CHART] = MermaidTextAttributes.diagram_name
      fillMap(
        holder,
        MermaidTextAttributes.keyword,
        MermaidTokens.XYChart.LINE_KEYWORD,
        MermaidTokens.XYChart.BAR_KEYWORD,
      )
      holder[MermaidTokens.XYChart.ORIENTATION_VALUE] = MermaidTextAttributes.constant
    }

    private fun addBlockHighlights(holder: MutableMap<IElementType, TextAttributesKey>) {
      holder[MermaidTokens.Block.BLOCK_DIAGRAM] = MermaidTextAttributes.diagram_name
      fillMap(
        holder,
        MermaidTextAttributes.keyword,
        MermaidTokens.Block.BLOCK,
        MermaidTokens.Block.COLUMNS,
        MermaidTokens.Block.SPACE,
      )
      fillMap(
        holder,
        MermaidTextAttributes.constant,
        MermaidTokens.Block.ARROW_DIR,
        MermaidTokens.Block.AUTO
      )
      fillMap(
        holder,
        MermaidTextAttributes.operator,
        MermaidTokens.Block.ARROW_DESCR_START,
        MermaidTokens.Block.ARROW_DESCR_END,
      )
    }

    private fun addBaseHighlights(holder: MutableMap<IElementType, TextAttributesKey>) {
      fillMap(
        holder,
        MermaidTextAttributes.keyword,
        MermaidTokens.END,
        MermaidTokens.TITLE,
        MermaidTokens.CLASS,
        MermaidTokens.DIRECTION,
        MermaidTokens.AS,
        MermaidTokens.NOTE,
        MermaidTokens.RIGHT_OF,
        MermaidTokens.LEFT_OF,
        MermaidTokens.SECTION,
        MermaidTokens.LINK,
        MermaidTokens.ID_KEYWORD,
        MermaidTokens.TYPE,
        MermaidTokens.ACC_TITLE,
        MermaidTokens.ACC_DESCR,
        MermaidTokens.CLICK,
        MermaidTokens.CALLBACK,
        MermaidTokens.Mindmap.OPEN_ICON,
        MermaidTokens.Mindmap.CLOSE_ICON,
        MermaidTokens.STYLE_OPT,
        MermaidTokens.X_AXIS,
        MermaidTokens.Y_AXIS,
        MermaidTokens.STYLE,
      )
      fillMap(
        holder,
        MermaidTextAttributes.string,
        MermaidTokens.DOUBLE_QUOTE,
        MermaidTokens.STRING_VALUE,
        MermaidTokens.MD_STRING_VALUE,
        MermaidTokens.ALIAS,
        MermaidTokens.LABEL,
        MermaidTokens.TASK_NAME,
        MermaidTokens.ACC_TITLE_VALUE,
        MermaidTokens.ACC_DESCR_VALUE,
        MermaidTokens.ACC_DESCR_MULTILINE_VALUE,
        MermaidTokens.STYLE_VAL,
      )

      holder[MermaidTokens.TITLE_VALUE] = MermaidTextAttributes.title
      holder[MermaidTokens.NOTE_CONTENT] = MermaidTextAttributes.note
      fillMap(
        holder,
        MermaidTextAttributes.comment,
        MermaidTokens.LINE_COMMENT,
        MermaidTokens.IGNORED
      )
      fillMap(
        holder,
        MermaidTextAttributes.identifier,
        MermaidTokens.ID,
        MermaidTokens.SECTION_TITLE,
        MermaidTokens.STYLE_TARGET,
      )
      fillMap(
        holder,
        MermaidTextAttributes.operator,
        MermaidTokens.PLUS,
        MermaidTokens.MINUS,
        MermaidTokens.TILDA,
        MermaidTokens.STAR,
        MermaidTokens.POUND,
        MermaidTokens.DOLLAR,
        MermaidTokens.STYLE_SEPARATOR,
        MermaidTokens.NODE_DESCR_START,
        MermaidTokens.NODE_DESCR_END,
      )
      fillMap(
        holder,
        MermaidTextAttributes.edge,
        MermaidTokens.ARROW,
        MermaidTokens.START_ARROW,
      )
      fillMap(
        holder,
        MermaidTextAttributes.constant,
        MermaidTokens.DIR,
        MermaidTokens.ANNOTATION_START,
        MermaidTokens.ANNOTATION_VALUE,
        MermaidTokens.ANNOTATION_END,
        MermaidTokens.NUM
      )
      fillMap(
        holder,
        MermaidTextAttributes.frontmatter_delimiter,
        MermaidTokens.Frontmatter.FRONTMATTER_START,
        MermaidTokens.Frontmatter.FRONTMATTER_END,
      )
      holder[MermaidTokens.DEFAULT] = MermaidTextAttributes.constant
      holder[MermaidTokens.GENERIC_TYPE] = MermaidTextAttributes.generic
    }
    //endregion
  }
}
