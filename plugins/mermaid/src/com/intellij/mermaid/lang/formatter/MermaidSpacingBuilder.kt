// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.formatter

import com.intellij.formatting.SpacingBuilder
import com.intellij.mermaid.lang.MermaidLanguage
import com.intellij.mermaid.lang.formatter.settings.MermaidCustomCodeStyleSettings
import com.intellij.mermaid.lang.lexer.MermaidTokenTypeSets.DIAGRAM_BODIES_AND_BLOCKS
import com.intellij.mermaid.lang.lexer.MermaidTokenTypeSets.MINDMAP_STATEMENTS
import com.intellij.mermaid.lang.lexer.MermaidTokenTypeSets.STATEMENTS
import com.intellij.mermaid.lang.lexer.MermaidTokenTypeSets.STRUCTURED_STATEMENTS
import com.intellij.mermaid.lang.lexer.MermaidTokens
import com.intellij.mermaid.lang.parser.MermaidElements
import com.intellij.mermaid.lang.parser.ParserUtils
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.tree.TokenSet
import com.intellij.util.applyIf

internal object MermaidSpacingBuilder {
  //region Click
  private val CLICK_KEYWORDS_SPACE_AFTER = TokenSet.create(
    MermaidTokens.CLICK,
    MermaidTokens.HREF,
    MermaidTokens.CALL,
    MermaidElements.CALLBACK_ARGS,
    MermaidTokens.LINK
  )
  //endregion

  //region Note
  private val NOTE_KEYWORDS_SPACE_AFTER = TokenSet.create(
    MermaidTokens.NOTE,
    MermaidTokens.LEFT_OF,
    MermaidTokens.RIGHT_OF,
    MermaidTokens.Sequence.OVER
  )
  //endregion

  //region Flowchart
  private val FLOWCHART_KEYWORDS_SPACE_AFTER = TokenSet.create(
    MermaidTokens.Flowchart.SUBGRAPH,
    MermaidTokens.STYLE,
    MermaidTokens.Flowchart.LINK_STYLE,
    MermaidTokens.CLASS_DEF,
    MermaidTokens.CLASS,
    MermaidElements.NODE_STATEMENT
  )
  private val FLOWCHART_KEYWORDS_SPACE_BEFORE = TokenSet.create(
    MermaidTokens.STYLE_TARGET,
    MermaidElements.STYLE_OPTIONS,
  )
  //endregion

  //region Sequence
  private val SEQUENCE_KEYWORDS_SPACE_AFTER = TokenSet.create(
    MermaidTokens.Sequence.LOOP,
    MermaidTokens.Sequence.PARTICIPANT,
    MermaidTokens.Sequence.ACTOR,
    MermaidTokens.Sequence.ACTIVATE,
    MermaidTokens.Sequence.DEACTIVATE,
    MermaidTokens.Sequence.LINKS,
    MermaidTokens.Sequence.RECT,
    MermaidTokens.Sequence.OPT,
    MermaidTokens.Sequence.ALT,
    MermaidTokens.Sequence.PAR,
    MermaidTokens.Sequence.PAR_OVER,
    MermaidTokens.Sequence.ELSE,
    MermaidTokens.Sequence.AND,
    MermaidTokens.Sequence.CRITICAL,
    MermaidTokens.Sequence.OPTION,
    MermaidTokens.Sequence.BREAK,
    MermaidTokens.Sequence.AUTONUMBER,
    MermaidTokens.Sequence.BOX,
  )
  private val SEQUENCE_KEYWORDS_SPACE_AROUND = TokenSet.create(
    MermaidTokens.Sequence.MESSAGE,
    MermaidTokens.Sequence.CONTROL_ID,
    MermaidTokens.AS
  )
  //endregion

  //region C4
  private val C4_KEYWORDS_SPACE_AFTER = TokenSet.create(
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
  //endregion

  //region Git graph
  private val GIT_GRAPH_ATTRIBUTES =
    TokenSet.create(
      MermaidElements.COMMIT_ID_ATTRIBUTE,
      MermaidElements.COMMIT_TAG_ATTRIBUTE,
      MermaidElements.COMMIT_TYPE_ATTRIBUTE,
      MermaidElements.COMMIT_MSG_ATTRIBUTE,
      MermaidElements.BRANCH_ORDER
    )
  //endregion

  //region Block diagram
  private val BLOCK_INNER_STMTS =
    TokenSet.create(
      MermaidElements.BLOCK_DIAGRAM_NODE_STATEMENT_INNER,
      MermaidElements.SPACE_STATEMENT_INNER,
    )
  //endregion

  //region OPEN CURLY STRUCTURES
  private val OPEN_CURLY_STRUCTURES =
    TokenSet.create(
      MermaidElements.CLASS_BLOCK,
      MermaidElements.NAMESPACE_BLOCK,
      MermaidElements.STATE_BLOCK,
      MermaidElements.ER_ENTITY_BLOCK,
      MermaidElements.REQUIREMENT_BLOCK,
      MermaidElements.ELEMENT_BLOCK,
      MermaidElements.BOUNDARY_BLOCK,
    )
  //endregion

  fun get(settings: CodeStyleSettings): SpacingBuilder {
    val mermaid = settings.getCustomSettings(MermaidCustomCodeStyleSettings::class.java)
    return SpacingBuilder(settings, MermaidLanguage)
      .addRulesRequiredByMermaid()
      .addCustomizableRules(mermaid)
      .applyIf(mermaid.FORCE_ONE_SPACE_BETWEEN_WORDS) {
        addForceOneSpaceBetweenWordsRule()
      }
      .addBlankLinesRules(mermaid)
  }

  private fun SpacingBuilder.addCustomizableRules(mermaid: MermaidCustomCodeStyleSettings): SpacingBuilder {
    return around(MermaidTokens.STYLE_SEPARATOR).spaceIf(mermaid.AROUND_STYLE_SEPARATOR)

      .between(MermaidTokens.ATTRIBUTE_WORD, MermaidElements.GENERIC).spaceIf(mermaid.BEFORE_GENERIC)
      .between(MermaidElements.CLASS_DIAGRAM_IDENTIFIER, MermaidElements.GENERIC).spaceIf(mermaid.BEFORE_GENERIC)
      .between(MermaidTokens.ATTRIBUTE_WORD, MermaidTokens.TILDA).spaceIf(mermaid.BEFORE_GENERIC)

      .around(MermaidTokens.C4.EQUALITY).spaceIf(mermaid.AROUND_EQUALITY)
      .before(MermaidTokens.COMMA).spaceIf(mermaid.BEFORE_COMMA)
      .after(MermaidTokens.COMMA).spaceIf(mermaid.AFTER_COMMA)
      .before(MermaidTokens.COLON).spaceIf(mermaid.BEFORE_COLON)
      .after(MermaidTokens.COLON).spaceIf(mermaid.AFTER_COLON)
      .between(STATEMENTS, MermaidTokens.SEMICOLON).spaceIf(mermaid.BEFORE_SEMICOLON)

      .before(MermaidTokens.OPEN_CURLY).spaceIf(mermaid.BEFORE_OPEN_CURLY)
      .between(MermaidElements.CLASS_HEADER, MermaidElements.CLASS_BLOCK).spaceIf(mermaid.BEFORE_OPEN_CURLY)
      .between(MermaidElements.NAMESPACE_HEADER, MermaidElements.NAMESPACE_BLOCK).spaceIf(mermaid.BEFORE_OPEN_CURLY)
      .between(MermaidElements.STATE_DECLARATION_HEADER, MermaidElements.STATE_BLOCK).spaceIf(mermaid.BEFORE_OPEN_CURLY)
      .between(MermaidElements.ER_IDENTIFIER, MermaidElements.ER_ENTITY_BLOCK).spaceIf(mermaid.BEFORE_OPEN_CURLY)
      .between(MermaidElements.ER_IDENTIFIER_ALIAS, MermaidElements.ER_ENTITY_BLOCK).spaceIf(mermaid.BEFORE_OPEN_CURLY)
      .between(MermaidElements.REQUIREMENT_HEADER, MermaidElements.REQUIREMENT_BLOCK).spaceIf(mermaid.BEFORE_OPEN_CURLY)
      .between(MermaidElements.ELEMENT_HEADER, MermaidElements.ELEMENT_BLOCK).spaceIf(mermaid.BEFORE_OPEN_CURLY)
      .between(MermaidElements.BOUNDARY_HEADER, MermaidElements.BOUNDARY_BLOCK).spaceIf(mermaid.BEFORE_OPEN_CURLY)

      .before(MermaidTokens.OPEN_ROUND).spaceIf(mermaid.BEFORE_OPEN_ROUND)
      .after(C4_KEYWORDS_SPACE_AFTER).spaceIf(mermaid.BEFORE_OPEN_ROUND)
      .between(MermaidTokens.ATTRIBUTE_WORD, MermaidTokens.OPEN_ROUND).spaceIf(mermaid.BEFORE_OPEN_ROUND)
      .before(MermaidElements.CALLBACK_ARGS).spaceIf(mermaid.BEFORE_OPEN_ROUND)

      .after(MermaidTokens.OPEN_SQUARE).spaceIf(mermaid.WITHIN_SQUARE)
      .before(MermaidTokens.CLOSE_SQUARE).spaceIf(mermaid.WITHIN_SQUARE)

      .after(MermaidTokens.OPEN_ROUND).spaceIf(mermaid.WITHIN_ROUND)
      .before(MermaidTokens.CLOSE_ROUND).spaceIf(mermaid.WITHIN_ROUND)

      .afterInside(MermaidTokens.OPEN_CURLY, OPEN_CURLY_STRUCTURES).spaceIfAndBlankLinesRange(mermaid.WITHIN_CURLY, mermaid.KEEP_LINES_WITHIN_STRUCTURES, mermaid.MIN_LINES_WITHIN_STRUCTURES)
      .beforeInside(MermaidTokens.CLOSE_CURLY, OPEN_CURLY_STRUCTURES).spaceIfAndBlankLinesRange(mermaid.WITHIN_CURLY, mermaid.KEEP_LINES_WITHIN_STRUCTURES, mermaid.MIN_LINES_WITHIN_STRUCTURES)

      .after(MermaidTokens.OPEN_CURLY).spaceIf(mermaid.WITHIN_CURLY)
      .before(MermaidTokens.CLOSE_CURLY).spaceIf(mermaid.WITHIN_CURLY)

      // Node text within braces
      .around(MermaidElements.NODE_TEXT).spaceIf(mermaid.WITHIN_NODE_SHAPES)
      .after(MermaidTokens.NODE_DESCR_START).spaceIf(mermaid.WITHIN_NODE_SHAPES)
      .before(MermaidTokens.NODE_DESCR_END).spaceIf(mermaid.WITHIN_NODE_SHAPES)

      .aroundInside(MermaidElements.STRING, MermaidElements.CLASS_LABEL).spaceIf(mermaid.WITHIN_NODE_SHAPES)

      .between(MermaidElements.MINDMAP_NODE_ID, MermaidElements.MINDMAP_NODE_DESCR)
      .spaceIf(mermaid.BETWEEN_NODE_ID_AND_NODE_SHAPE)

      .between(MermaidElements.COMPLEX_IDENTIFIER, MermaidElements.VERTEX_TEXT)
      .spaceIf(mermaid.BETWEEN_NODE_ID_AND_NODE_SHAPE)

      .between(MermaidElements.CLASS_DIAGRAM_IDENTIFIER, MermaidElements.CLASS_LABEL)
      .spaceIf(mermaid.BETWEEN_NODE_ID_AND_NODE_SHAPE)

      .between(MermaidElements.ER_IDENTIFIER, MermaidElements.ER_IDENTIFIER_ALIAS)
      .spaceIf(mermaid.BETWEEN_NODE_ID_AND_NODE_SHAPE)

      // Annotation
      .around(MermaidTokens.ANNOTATION_VALUE).spaceIf(mermaid.WITHIN_ANNOTATION_BRACES)

      .between(MermaidElements.STATE_DECLARATION_HEADER, MermaidElements.ANNOTATION)
      .spaceIf(mermaid.BETWEEN_STATE_AND_ANNOTATION)

      // Flowchart link text
      .between(MermaidTokens.Flowchart.SEP, MermaidElements.COMPLEX_LINK_TEXT).spaceIf(mermaid.WITHIN_ARROW_TEXT_SEP)
      .between(MermaidElements.COMPLEX_LINK_TEXT, MermaidTokens.Flowchart.SEP).spaceIf(mermaid.WITHIN_ARROW_TEXT_SEP)
      .around(MermaidElements.COMPLEX_LINK_TEXT).spaceIf(mermaid.AROUND_INLINE_ARROW_TEXT)
      .before(MermaidTokens.Flowchart.SEP).spaceIf(mermaid.BEFORE_ARROW_TEXT_WITHIN_SEP)
      .after(MermaidTokens.Flowchart.SEP).spaceIf(mermaid.AFTER_ARROW_TEXT_WITHIN_SEP)

      // Arrows
      .around(MermaidElements.FLOWCHART_LINK_STATEMENT).spaceIf(mermaid.AROUND_ARROW)
      .around(MermaidTokens.ARROW).spaceIf(mermaid.AROUND_ARROW)
      .after(MermaidTokens.START_ARROW).spaceIf(mermaid.AROUND_ARROW)
      .around(MermaidElements.SIGNAL).spaceIf(mermaid.AROUND_ARROW)
      .around(MermaidElements.CARDINALITY).spaceIf(mermaid.AROUND_ARROW)
      .around(MermaidElements.RELATION).spaceIf(mermaid.AROUND_ARROW)
      .around(MermaidElements.RELATIONSHIP).spaceIf(mermaid.AROUND_ARROW)
      .around(MermaidTokens.Requirement.ARROW_LEFT).spaceIf(mermaid.AROUND_ARROW)
      .around(MermaidTokens.Requirement.ARROW_RIGHT).spaceIf(mermaid.AROUND_ARROW)
      .around(MermaidTokens.Requirement.REQ_LINE).spaceIf(mermaid.AROUND_ARROW)

      .after(MermaidElements.RELATION_TYPE_LEFT).spaceIf(mermaid.BEETWEEN_LINE_TYPE_AND_RELATION_TYPE)
      .before(MermaidElements.RELATION_TYPE_RIGHT).spaceIf(mermaid.BEETWEEN_LINE_TYPE_AND_RELATION_TYPE)
  }

  private fun SpacingBuilder.addRulesRequiredByMermaid(): SpacingBuilder {
    return between(MermaidTokens.COMMA, MermaidTokens.STYLE_TARGET).spaceIf(false)
      .between(MermaidTokens.COMMA, MermaidTokens.Flowchart.CLASS_ID_STYLE).spaceIf(false)
      .between(MermaidTokens.COMMA, MermaidElements.QUOTED_SANKEY_FIELD).spaceIf(false)
      .between(MermaidTokens.COMMA, MermaidElements.IDENTIFYING_QUOTED_SANKEY_FIELD).spaceIf(false)
      .aroundInside(MermaidTokens.STYLE_SEPARATOR, MermaidElements.STYLED_VERTEX).spaceIf(false)
      .around(MermaidElements.GENERIC_TYPE_ID).spaceIf(false)
      .around(ParserUtils.DIRECTIVE_VALUE).spaceIf(false)
      .around(MermaidTokens.Directives.CLOSE_DIRECTIVE).spaceIf(false)
      .after(MermaidTokens.Directives.OPEN_DIRECTIVE).spaceIf(false)
      .beforeInside(MermaidTokens.COLON, GIT_GRAPH_ATTRIBUTES).spaceIf(false)
      .between(MermaidTokens.Block.BLOCK, MermaidTokens.COLON).spaceIf(false)
      .between(MermaidElements.BLOCK_DIAGRAM_NODE, MermaidElements.BLOCK_SIZE).spaceIf(false)
      .between(MermaidTokens.Block.SPACE, MermaidElements.BLOCK_SIZE).spaceIf(false)
      .betweenInside(MermaidTokens.COLON, MermaidTokens.NUM, MermaidElements.BLOCK_SIZE).spaceIf(false)
      .before(MermaidTokens.IGNORED).spaceIf(false)
  }

  private fun SpacingBuilder.addForceOneSpaceBetweenWordsRule(): SpacingBuilder {
    return before(MermaidTokens.DIR).spaces(1)
      .after(NOTE_KEYWORDS_SPACE_AFTER).spaces(1)
      // Click
      .after(CLICK_KEYWORDS_SPACE_AFTER).spaces(1)
      .before(MermaidTokens.LINK_TARGET).spaces(1)
      .between(MermaidElements.STRING, MermaidElements.STRING).spaces(1)
      .between(MermaidTokens.CLICK_DATA, MermaidTokens.CALL).spaces(1)
      .between(MermaidTokens.CLICK_DATA, MermaidTokens.HREF).spaces(1)
      .between(MermaidTokens.CLICK_DATA, MermaidElements.STRING).spaces(1)
      .between(MermaidTokens.CLICK_DATA, MermaidTokens.CLICK_DATA).spaces(1)
      .between(MermaidTokens.TITLE_VALUE, MermaidTokens.TITLE_VALUE).spaces(1)
      // Title
      .after(MermaidTokens.TITLE).spaces(1)
      // Flowchart
      .between(MermaidTokens.ALIAS, MermaidTokens.ALIAS).spaces(1)
      .around(MermaidTokens.Flowchart.AMPERSAND).spaces(1)
      .around(MermaidTokens.Flowchart.LINK_TEXT).spaces(1)
      .after(FLOWCHART_KEYWORDS_SPACE_AFTER).spaces(1)
      .before(FLOWCHART_KEYWORDS_SPACE_BEFORE).spaces(1)
      // Sequence
      .after(SEQUENCE_KEYWORDS_SPACE_AFTER).spaces(1)
      .around(SEQUENCE_KEYWORDS_SPACE_AROUND).spaces(1)
      // Class diagram
      .between(MermaidElements.GENERIC, MermaidTokens.ATTRIBUTE_WORD).spaces(1)
      .between(MermaidTokens.TILDA, MermaidTokens.ATTRIBUTE_WORD).spaces(1)
      .between(MermaidTokens.ATTRIBUTE_WORD, MermaidTokens.ATTRIBUTE_WORD).spaces(1)
      .between(MermaidTokens.CLOSE_ROUND, MermaidTokens.ATTRIBUTE_WORD).spaces(1)
      .between(MermaidElements.ANNOTATION, MermaidElements.CLASS_DIAGRAM_IDENTIFIER).spaces(1)

      .between(MermaidTokens.ClassDiagram.NOTE_FOR, MermaidElements.CLASS_DIAGRAM_IDENTIFIER).spaces(1)
      .between(MermaidElements.CLASS_DIAGRAM_IDENTIFIER, MermaidElements.CLASS_DIAGRAM_NOTE_TEXT).spaces(1)
      .after(MermaidElements.CLASS_DIAGRAM_IDENTIFIER).spaces(1)
      // State diagram
      .after(MermaidTokens.StateDiagram.STATE).spaces(1)
      .after(MermaidElements.SPECIAL_STATE).spaces(1)
      .between(MermaidTokens.NOTE_CONTENT, MermaidTokens.NOTE_CONTENT).spaces(1)
      .between(MermaidTokens.LABEL, MermaidTokens.LABEL).spaces(1)
      // Entity Relationship
      .around(MermaidElements.ATTR_KEYS).spaces(1)
      .before(MermaidElements.ATTR_NAME).spaces(1)
      .between(MermaidElements.ATTR_NAME, MermaidTokens.EntityRelationship.ATTR_KEY).spaces(1)
      .between(MermaidElements.ATTR_NAME, MermaidElements.STRING).spaces(1)
      .after(MermaidElements.ER_IDENTIFIER).spaces(1)
      // User Journey
      .after(MermaidTokens.TASK_NAME).spaces(1)
      .after(MermaidTokens.SECTION).spaces(1)
      .between(MermaidTokens.TASK_NAME, MermaidTokens.TASK_NAME).spaces(1)
      .between(MermaidTokens.TASK_DATA, MermaidTokens.TASK_DATA).spaces(1)
      .between(MermaidTokens.SECTION_TITLE, MermaidTokens.SECTION_TITLE).spaces(1)
      // Pie
      .before(MermaidTokens.Pie.SHOW_DATA).spaces(1)
      // Requirement
      .after(MermaidElements.REQUIREMENT_TYPE).spaces(1)
      .after(MermaidTokens.Requirement.ELEMENT).spaces(1)
      // Git Graph
      .after(MermaidTokens.GitGraph.BRANCH).spaces(1)
      .after(MermaidTokens.GitGraph.CHERRY_PICK).spaces(1)
      .after(MermaidTokens.GitGraph.MERGE).spaces(1)
      .after(MermaidTokens.GitGraph.CHECKOUT).spaces(1)
      .after(MermaidTokens.GitGraph.COMMIT).spaces(1)
      .around(GIT_GRAPH_ATTRIBUTES).spaces(1)
      // Mindmap
      .between(MermaidTokens.Mindmap.NODE_DESCR, MermaidTokens.Mindmap.NODE_DESCR).spaces(1)
      .betweenInside(MermaidTokens.ID, MermaidTokens.ID, MermaidElements.MINDMAP_NODE_ID).spaces(1)
      // Quadrant
      .after(MermaidTokens.X_AXIS).spaces(1)
      .after(MermaidTokens.Y_AXIS).spaces(1)
      .between(MermaidTokens.Quadrant.QUADRANT_TEXT, MermaidTokens.Quadrant.QUADRANT_TEXT).spaces(1)
      // Gantt
      .between(MermaidTokens.Gantt.DATE_FORMAT, MermaidTokens.Gantt.GANTT_VALUE).spaces(1)
      .between(MermaidTokens.Gantt.TODAY_MARKER, MermaidTokens.Gantt.GANTT_VALUE).spaces(1)
      .between(MermaidTokens.Gantt.TICK_INTERVAL, MermaidTokens.Gantt.GANTT_VALUE).spaces(1)
      .between(MermaidTokens.Gantt.INCLUDES, MermaidTokens.Gantt.GANTT_VALUE).spaces(1)
      .between(MermaidTokens.Gantt.EXCLUDES, MermaidTokens.Gantt.GANTT_VALUE).spaces(1)
      .between(MermaidTokens.Gantt.AXIS_FORMAT, MermaidTokens.Gantt.GANTT_VALUE).spaces(1)
      // Sankey
      .between(MermaidTokens.Sankey.SANKEY_TEXT, MermaidTokens.Sankey.SANKEY_TEXT).spaces(1)
      // XYChart
      .between(MermaidTokens.XYChart.XY_CHART, MermaidTokens.XYChart.ORIENTATION_VALUE).spaces(1)
      .after(MermaidTokens.XYChart.LINE_KEYWORD).spaces(1)
      .after(MermaidTokens.XYChart.BAR_KEYWORD).spaces(1)
      // Block
      .after(MermaidTokens.Block.COLUMNS).spaces(1)
      .after(MermaidTokens.Block.SPACE).spaces(1)
      .between(BLOCK_INNER_STMTS, BLOCK_INNER_STMTS).spaces(1)
  }

  private fun SpacingBuilder.addBlankLinesRules(mermaid: MermaidCustomCodeStyleSettings): SpacingBuilder {
    return between(STRUCTURED_STATEMENTS, STRUCTURED_STATEMENTS)
      .blankLinesRange(mermaid.KEEP_LINES_BETWEEN_STRUCTURED_STATEMENTS, mermaid.MIN_LINES_BETWEEN_STRUCTURED_STATEMENTS)

      .around(STRUCTURED_STATEMENTS)
      .blankLinesRange(mermaid.KEEP_LINES_AROUND_STRUCTURED_STATEMENTS, mermaid.MIN_LINES_AROUND_STRUCTURED_STATEMENTS)

      .between(MINDMAP_STATEMENTS, MINDMAP_STATEMENTS)
      .blankLinesRange(mermaid.KEEP_LINES_BETWEEN_OTHER_STATEMENTS, mermaid.MIN_LINES_BETWEEN_OTHER_STATEMENTS)

      .between(STATEMENTS, STATEMENTS)
      .blankLinesRange(mermaid.KEEP_LINES_BETWEEN_OTHER_STATEMENTS, mermaid.MIN_LINES_BETWEEN_OTHER_STATEMENTS)

      .around(STATEMENTS)
      .blankLinesRange(mermaid.KEEP_LINES_BETWEEN_OTHER_STATEMENTS, mermaid.MIN_LINES_BETWEEN_OTHER_STATEMENTS)

      .before(DIAGRAM_BODIES_AND_BLOCKS)
      .blankLinesRange(mermaid.KEEP_LINES_WITHIN_STRUCTURES, mermaid.MIN_LINES_WITHIN_STRUCTURES)
  }

  private fun SpacingBuilder.RuleBuilder.blankLinesRange(from: Int, to: Int): SpacingBuilder {
    return spacing(0, 0, to + 1, false, from)
  }

  private fun SpacingBuilder.RuleBuilder.spaceIfAndBlankLinesRange(option: Boolean, from: Int, to: Int): SpacingBuilder {
    val spaces = if (option) 1 else 0
    return spacing(spaces, spaces, to + 1, false, from)
  }
}
