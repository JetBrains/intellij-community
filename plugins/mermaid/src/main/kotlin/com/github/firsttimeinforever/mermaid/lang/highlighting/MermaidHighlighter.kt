package com.github.firsttimeinforever.mermaid.lang.highlighting

import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidLexer
import com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens
import com.intellij.lexer.Lexer
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
      MermaidTokens.Sequence.AUTONUMBER,
      MermaidTokens.Sequence.LINK,
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
      MermaidTokens.ClassDiagram.CLASS_DIAGRAM -> arrayOf(MermaidTextAttributes.keyword)

      MermaidTokens.ClassDiagram.EXTENSION_START,
      MermaidTokens.ClassDiagram.EXTENSION_END,
      MermaidTokens.ClassDiagram.DEPENDENCY_START,
      MermaidTokens.ClassDiagram.DEPENDENCY_END,
      MermaidTokens.ClassDiagram.COMPOSITION,
      MermaidTokens.ClassDiagram.AGGREGATION,
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

      MermaidTokens.EntityRelationship.ZERO_OR_ONE_LEFT,
      MermaidTokens.EntityRelationship.ONE_OR_MORE_LEFT,
      MermaidTokens.EntityRelationship.ZERO_OR_MORE_LEFT,
      MermaidTokens.EntityRelationship.ONLY_ONE,
      MermaidTokens.EntityRelationship.ZERO_OR_ONE_RIGHT,
      MermaidTokens.EntityRelationship.ONE_OR_MORE_RIGHT,
      MermaidTokens.EntityRelationship.ZERO_OR_MORE_RIGHT,
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
      MermaidTokens.Requirement.ID_KEYWORD,
      MermaidTokens.Requirement.TEXT,
      MermaidTokens.Requirement.RISK,
      MermaidTokens.Requirement.VERIFY_METHOD,
      MermaidTokens.Requirement.TYPE,
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
    return pieHighlights
      ?: journeyHighlighter
      ?: flowchartHighlighter
      ?: sequenceHighlighter
      ?: classDiagramHighlights
      ?: stateDiagramHighlights
      ?: entityRelationshipDiagramHighlights
      ?: ganttDiagramHighlights
      ?: requirementHighlights
      ?: when (tokenType) {
        MermaidTokens.END,
        MermaidTokens.TITLE,
        MermaidTokens.CLASS,
        MermaidTokens.DIRECTION,
        MermaidTokens.AS,
        MermaidTokens.NOTE,
        MermaidTokens.RIGHT_OF,
        MermaidTokens.LEFT_OF,
        MermaidTokens.SECTION -> arrayOf(MermaidTextAttributes.keyword)

        MermaidTokens.TITLE_VALUE,
        MermaidTokens.DOUBLE_QUOTE,
        MermaidTokens.STRING_VALUE,
        MermaidTokens.ALIAS,
        MermaidTokens.LABEL,
        MermaidTokens.SECTION_TITLE,
        MermaidTokens.TASK_NAME-> arrayOf(MermaidTextAttributes.string)

        MermaidTokens.LINE_COMMENT,
        MermaidTokens.COMMENT_TEXT,
        MermaidTokens.IGNORED -> arrayOf(MermaidTextAttributes.comment)

        MermaidTokens.ID -> arrayOf(MermaidTextAttributes.identifier)

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
