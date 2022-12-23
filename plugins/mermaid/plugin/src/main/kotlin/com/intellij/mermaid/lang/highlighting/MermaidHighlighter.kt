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

  private fun getPieHighlights(tokenType: IElementType): Array<TextAttributesKey>? {
    return when (tokenType) {
      MermaidTokens.Pie.PIE,
      MermaidTokens.Pie.SHOW_DATA -> arrayOf(MermaidTextAttributes.keyword)
      else -> null
    }
  }

  private fun getJourneyHighlights(tokenType: IElementType): Array<TextAttributesKey>? {
    return when (tokenType) {
      MermaidTokens.Journey.JOURNEY -> arrayOf(MermaidTextAttributes.keyword)
      else -> null
    }
  }

  private fun getFlowchartHighlights(tokenType: IElementType): Array<TextAttributesKey>? {
    return when (tokenType) {
      MermaidTokens.Flowchart.FLOWCHART,
      MermaidTokens.Flowchart.SUBGRAPH,
      MermaidTokens.Flowchart.STYLE,
      MermaidTokens.Flowchart.STYLE_OPT,
      MermaidTokens.Flowchart.CLASS_DEF -> arrayOf(MermaidTextAttributes.keyword)

      MermaidTokens.Flowchart.START_ARROW -> arrayOf(MermaidTextAttributes.operationSign)

      MermaidTokens.Flowchart.STYLE_VAL -> arrayOf(MermaidTextAttributes.string)

      MermaidTokens.Flowchart.STYLE_TARGET -> arrayOf(MermaidTextAttributes.identifier)

      else -> null
    }
  }

  private fun getSequenceHighlights(tokenType: IElementType): Array<TextAttributesKey>? {
    return when (tokenType) {
      MermaidTokens.Sequence.SEQUENCE,
      MermaidTokens.Sequence.PARTICIPANT,
      MermaidTokens.Sequence.ACTOR,
      MermaidTokens.Sequence.ACTIVATE,
      MermaidTokens.Sequence.DEACTIVATE,
      MermaidTokens.Sequence.OVER,
      MermaidTokens.Sequence.LOOP,
      MermaidTokens.Sequence.ALT,
      MermaidTokens.Sequence.ELSE,
      MermaidTokens.Sequence.OPT,
      MermaidTokens.Sequence.PAR,
      MermaidTokens.Sequence.AND,
      MermaidTokens.Sequence.RECT,
      MermaidTokens.Sequence.CRITICAL,
      MermaidTokens.Sequence.OPTION,
      MermaidTokens.Sequence.BREAK,
      MermaidTokens.Sequence.AUTONUMBER,
      MermaidTokens.Sequence.OFF,
      MermaidTokens.Sequence.LINKS -> arrayOf(MermaidTextAttributes.keyword)

      MermaidTokens.Sequence.SOLID_ARROW,
      MermaidTokens.Sequence.DOTTED_ARROW,
      MermaidTokens.Sequence.SOLID_OPEN_ARROW,
      MermaidTokens.Sequence.DOTTED_OPEN_ARROW,
      MermaidTokens.Sequence.SOLID_CROSS,
      MermaidTokens.Sequence.DOTTED_CROSS,
      MermaidTokens.Sequence.SOLID_POINT,
      MermaidTokens.Sequence.DOTTED_POINT -> arrayOf(MermaidTextAttributes.operationSign)

      MermaidTokens.Sequence.MESSAGE -> arrayOf(MermaidTextAttributes.string)

      else -> null
    }
  }

  private fun getClassDiagramHighlights(tokenType: IElementType): Array<TextAttributesKey>? {
    return when (tokenType) {
      MermaidTokens.ClassDiagram.CLASS_DIAGRAM,
      MermaidTokens.ClassDiagram.CLASS_ID -> arrayOf(MermaidTextAttributes.keyword)

      MermaidTokens.ClassDiagram.EXTENSION_START,
      MermaidTokens.ClassDiagram.EXTENSION_END,
      MermaidTokens.ClassDiagram.DEPENDENCY_START,
      MermaidTokens.ClassDiagram.DEPENDENCY_END,
      MermaidTokens.ClassDiagram.COMPOSITION,
      MermaidTokens.ClassDiagram.AGGREGATION,
      MermaidTokens.ClassDiagram.LOLLIPOP,
      MermaidTokens.ClassDiagram.LINE,
      MermaidTokens.ClassDiagram.DOTTED_LINE -> arrayOf(MermaidTextAttributes.operationSign)

      else -> null
    }
  }

  private fun getStateDiagramHighlights(tokenType: IElementType): Array<TextAttributesKey>? {
    return when (tokenType) {
      MermaidTokens.StateDiagram.STATE_DIAGRAM,
      MermaidTokens.StateDiagram.STATE -> arrayOf(MermaidTextAttributes.keyword)

      else -> null
    }
  }

  private fun getEntityRelationshipDiagramHighlights(tokenType: IElementType): Array<TextAttributesKey>? {
    return when (tokenType) {
      MermaidTokens.EntityRelationship.ENTITY_RELATIONSHIP -> arrayOf(MermaidTextAttributes.keyword)

      MermaidTokens.EntityRelationship.ZERO_OR_ONE,
      MermaidTokens.EntityRelationship.ONE_OR_MORE,
      MermaidTokens.EntityRelationship.ZERO_OR_MORE,
      MermaidTokens.EntityRelationship.ONLY_ONE,
      MermaidTokens.EntityRelationship.IDENTIFYING,
      MermaidTokens.EntityRelationship.NON_IDENTIFYING -> arrayOf(MermaidTextAttributes.operationSign)

      MermaidTokens.EntityRelationship.ATTR_KEY -> arrayOf(MermaidTextAttributes.constant)

      else -> null
    }
  }

  private fun getGanttDiagramHighlights(tokenType: IElementType): Array<TextAttributesKey>? {
    return when (tokenType) {
      MermaidTokens.Gantt.GANTT -> arrayOf(MermaidTextAttributes.keyword)


      else -> null
    }
  }

  private fun getRequirementHighlights(tokenType: IElementType): Array<TextAttributesKey>? {
    return when (tokenType) {
      MermaidTokens.Requirement.REQUIREMENT_DIAGRAM,
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
      MermaidTokens.Requirement.DOCREF -> arrayOf(MermaidTextAttributes.keyword)

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
      MermaidTokens.Requirement.TRACES -> arrayOf(MermaidTextAttributes.constant)

      MermaidTokens.Requirement.ARROW_LEFT,
      MermaidTokens.Requirement.ARROW_RIGHT,
      MermaidTokens.Requirement.REQ_LINE -> arrayOf(MermaidTextAttributes.operationSign)

      else -> null
    }
  }

  private fun getGitGraphHighlights(tokenType: IElementType): Array<TextAttributesKey>? {
    return when (tokenType) {
      MermaidTokens.GitGraph.GIT_GRAPH,
      MermaidTokens.GitGraph.COMMIT,
      MermaidTokens.GitGraph.BRANCH,
      MermaidTokens.GitGraph.CHECKOUT,
      MermaidTokens.GitGraph.MERGE,
      MermaidTokens.GitGraph.TAG,
      MermaidTokens.GitGraph.MSG,
      MermaidTokens.GitGraph.CHERRY_PICK,
      MermaidTokens.GitGraph.ORDER -> arrayOf(MermaidTextAttributes.keyword)

      MermaidTokens.GitGraph.NORMAL,
      MermaidTokens.GitGraph.REVERSE,
      MermaidTokens.GitGraph.HIGHLIGHT -> arrayOf(MermaidTextAttributes.constant)

      else -> null
    }
  }

  private fun getC4Highlights(tokenType: IElementType): Array<TextAttributesKey>? {
    return when (tokenType) {
      MermaidTokens.C4.C4_CONTEXT,
      MermaidTokens.C4.C4_CONTAINER,
      MermaidTokens.C4.C4_COMPONENT,
      MermaidTokens.C4.C4_DYNAMIC,
      MermaidTokens.C4.C4_DEPLOYMENT,
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
      MermaidTokens.C4.UPDATE_LAYOUT_CONFIG -> arrayOf(MermaidTextAttributes.keyword)

      MermaidTokens.C4.C4_ATTRIBUTE -> arrayOf(MermaidTextAttributes.identifier)

      MermaidTokens.C4.EQUALITY -> arrayOf(MermaidTextAttributes.operationSign)

      else -> null
    }
  }

  private fun getMindmapHighlights(tokenType: IElementType): Array<TextAttributesKey>? {
    return when (tokenType) {
      MermaidTokens.Mindmap.MINDMAP -> arrayOf(MermaidTextAttributes.keyword)

      MermaidTokens.Mindmap.NODE_DESCR,
      MermaidTokens.Mindmap.ICON_VALUE -> arrayOf(MermaidTextAttributes.string)

      MermaidTokens.Mindmap.NODE_DESCR_START,
      MermaidTokens.Mindmap.NODE_DESCR_END,
      MermaidTokens.Mindmap.OPEN_ICON,
      MermaidTokens.Mindmap.CLOSE_ICON -> arrayOf(MermaidTextAttributes.operationSign)

      else -> null
    }
  }

  override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
    val pieHighlights = getPieHighlights(tokenType)
    val journeyHighlighter = getJourneyHighlights(tokenType)
    val flowchartHighlighter = getFlowchartHighlights(tokenType)
    val sequenceHighlighter = getSequenceHighlights(tokenType)
    val classDiagramHighlights = getClassDiagramHighlights(tokenType)
    val stateDiagramHighlights = getStateDiagramHighlights(tokenType)
    val entityRelationshipDiagramHighlights = getEntityRelationshipDiagramHighlights(tokenType)
    val ganttDiagramHighlights = getGanttDiagramHighlights(tokenType)
    val requirementHighlights = getRequirementHighlights(tokenType)
    val gitGraphHighlights = getGitGraphHighlights(tokenType)
    val c4Highlights = getC4Highlights(tokenType)
    val mindmapHighlights = getMindmapHighlights(tokenType)
    return pieHighlights
      ?: journeyHighlighter
      ?: flowchartHighlighter
      ?: sequenceHighlighter
      ?: classDiagramHighlights
      ?: stateDiagramHighlights
      ?: entityRelationshipDiagramHighlights
      ?: ganttDiagramHighlights
      ?: requirementHighlights
      ?: gitGraphHighlights
      ?: c4Highlights
      ?: mindmapHighlights
      ?: when (tokenType) {
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
        MermaidTokens.ACC_DESCR -> arrayOf(MermaidTextAttributes.keyword)

        MermaidTokens.TITLE_VALUE,
        MermaidTokens.DOUBLE_QUOTE,
        MermaidTokens.STRING_VALUE,
        MermaidTokens.ALIAS,
        MermaidTokens.LABEL,
        MermaidTokens.SECTION_TITLE,
        MermaidTokens.TASK_NAME,
        MermaidTokens.ACC_TITLE_VALUE,
        MermaidTokens.ACC_DESCR_MULTILINE_VALUE -> arrayOf(MermaidTextAttributes.string)

        MermaidTokens.LINE_COMMENT,
        MermaidTokens.IGNORED -> arrayOf(MermaidTextAttributes.comment)

        MermaidTokens.ID,
        MermaidTokens.ATTRIBUTE_WORD -> arrayOf(MermaidTextAttributes.identifier)

        MermaidTokens.PLUS,
        MermaidTokens.MINUS,
        MermaidTokens.TILDA,
        MermaidTokens.STAR,
        MermaidTokens.POUND,
        MermaidTokens.DOLLAR,
        MermaidTokens.STYLE_SEPARATOR,
        MermaidTokens.ARROW -> arrayOf(MermaidTextAttributes.operationSign)

        MermaidTokens.DIR,
        MermaidTokens.ANNOTATION_START,
        MermaidTokens.ANNOTATION_VALUE,
        MermaidTokens.ANNOTATION_END -> arrayOf(MermaidTextAttributes.constant)

        else -> arrayOf(HighlighterColors.TEXT)
      }
  }
}
