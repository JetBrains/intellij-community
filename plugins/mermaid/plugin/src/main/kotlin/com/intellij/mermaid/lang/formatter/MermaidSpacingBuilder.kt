package com.intellij.mermaid.lang.formatter

import com.intellij.formatting.SpacingBuilder
import com.intellij.mermaid.lang.MermaidLanguage
import com.intellij.mermaid.lang.formatter.settings.MermaidCustomCodeStyleSettings
import com.intellij.mermaid.lang.lexer.MermaidTokens
import com.intellij.mermaid.lang.parser.MermaidElements
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.tree.TokenSet

internal object MermaidSpacingBuilder {
  private val CLICK_KEYWORDS_SPACE_AFTER = TokenSet.create(
    MermaidTokens.CLICK,
    MermaidTokens.HREF,
    MermaidTokens.CALL,
    MermaidElements.CALLBACK_ARGS
  )

  private val FLOWCHART_KEYWORDS_SPACE_AFTER = TokenSet.create(
    MermaidTokens.Flowchart.SUBGRAPH,
    MermaidTokens.Flowchart.STYLE,
    MermaidTokens.Flowchart.LINK_STYLE,
    MermaidTokens.Flowchart.CLASS_DEF,
    MermaidTokens.CLASS,
    MermaidElements.NODE_STATEMENT
  )
  private val FLOWCHART_KEYWORDS_SPACE_BEFORE = TokenSet.create(
    MermaidTokens.Flowchart.STYLE_TARGET,
    MermaidElements.STYLE_OPTIONS,
    MermaidElements.VERTEX_STATEMENT
  )

  private val SEQUENCE_KEYWORDS_SPACE_AFTER = TokenSet.create(
    MermaidTokens.Sequence.LOOP,
    MermaidTokens.Sequence.PARTICIPANT,
    MermaidTokens.Sequence.ACTOR,
    MermaidTokens.Sequence.ACTIVATE,
    MermaidTokens.Sequence.DEACTIVATE,
    MermaidTokens.Sequence.LINKS,
    MermaidTokens.Sequence.LOOP,
    MermaidTokens.Sequence.RECT,
    MermaidTokens.Sequence.OPT,
    MermaidTokens.Sequence.ALT,
    MermaidTokens.Sequence.PAR,
    MermaidTokens.Sequence.ELSE,
    MermaidTokens.Sequence.AND,
    MermaidTokens.Sequence.CRITICAL,
    MermaidTokens.Sequence.OPTION,
    MermaidTokens.Sequence.BREAK,
    MermaidTokens.Sequence.AUTONUMBER
  )
  private val SEQUENCE_KEYWORDS_SPACE_BEFORE = TokenSet.create(

  )

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

  fun get(settings: CodeStyleSettings): SpacingBuilder {
    val mermaid = settings.getCustomSettings(MermaidCustomCodeStyleSettings::class.java)
    val indentOptions = settings.getLanguageIndentOptions(MermaidLanguage)

    return SpacingBuilder(settings, MermaidLanguage)
      // Direction / common
      .before(MermaidTokens.OPEN_CURLY).spaces(1)
      .before(MermaidTokens.COMMA).spaceIf(false)
      .after(MermaidTokens.COMMA).spaceIf(true)
      .before(MermaidTokens.COLON).spaceIf(false)
      .after(MermaidTokens.COLON).spaceIf(true)
      .around(MermaidTokens.DOUBLE_QUOTE).spaceIf(false)
      .before(MermaidTokens.DIR).spaces(1)
      .after(MermaidTokens.NOTE).spaces(1)
      .after(MermaidTokens.LEFT_OF).spaces(1)
      .after(MermaidTokens.RIGHT_OF).spaces(1)
      .after(MermaidTokens.Sequence.OVER).spaces(1)
      .around(MermaidTokens.ALIAS).spaces(1)
      .around(MermaidElements.NODE_TEXT).spaceIf(false)
      .after(CLICK_KEYWORDS_SPACE_AFTER).spaces(1)
      .before(MermaidTokens.LINK_TARGET).spaces(1)
      .between(MermaidElements.STRING, MermaidElements.STRING).spaces(1)
      .between(MermaidTokens.CLICK_DATA, MermaidTokens.CALL).spaces(1)
      .between(MermaidTokens.CLICK_DATA, MermaidTokens.HREF).spaces(1)
      .between(MermaidTokens.CLICK_DATA, MermaidElements.STRING).spaces(1)
      .between(MermaidTokens.CLICK_DATA, MermaidTokens.CLICK_DATA).spaces(1)
      .between(MermaidTokens.TITLE_VALUE, MermaidTokens.TITLE_VALUE).spaces(1)
      .after(MermaidTokens.LINK).spaces(1)
      // Flowchart
      .around(MermaidElements.FLOWCHART_LINK_STATEMENT).spaces(1)
      .before(MermaidTokens.ARROW).spaces(1)
      .around(MermaidTokens.Flowchart.AMPERSAND).spaces(1)
      .around(MermaidTokens.Flowchart.LINK_TEXT).spaces(1)
      .after(MermaidTokens.Flowchart.START_ARROW).spaces(1)
      .after(FLOWCHART_KEYWORDS_SPACE_AFTER).spaces(1)
      .before(FLOWCHART_KEYWORDS_SPACE_BEFORE).spaces(1)
      .before(MermaidTokens.Flowchart.SEP).spaceIf(false)
      // Sequence
      .around(MermaidTokens.Sequence.MESSAGE).spaces(1)
      .after(SEQUENCE_KEYWORDS_SPACE_AFTER).spaces(1)
      .before(SEQUENCE_KEYWORDS_SPACE_BEFORE).spaces(1)
      .around(MermaidElements.SIGNAL).spaces(1)
      .around(MermaidTokens.AS).spaces(1)
      // Class diagram
      .around(MermaidElements.GENERIC_TYPE_ID).spaceIf(false)
      .around(MermaidElements.GENERIC).spacing(0, 1, 0, false, 0)
      .around(MermaidTokens.STYLE_SEPARATOR).spaceIf(false)
      .around(MermaidTokens.ANNOTATION_VALUE).spaceIf(false)
      .between(MermaidTokens.ATTRIBUTE_WORD, MermaidTokens.OPEN_ROUND).spaceIf(false)
      .between(MermaidTokens.OPEN_ROUND, MermaidTokens.ATTRIBUTE_WORD).spaceIf(false)
      .after(MermaidElements.RELATION_TYPE_LEFT).spaceIf(false)
      .before(MermaidElements.RELATION_TYPE_RIGHT).spaceIf(false)
      .around(MermaidElements.CARDINALITY).spaces(1)
      .around(MermaidElements.RELATION).spaces(1)
      .before(MermaidElements.ATTRIBUTE).spaceIf(false)
      .before(MermaidElements.MEMBER_ATTRIBUTE).spaceIf(false)
      .between(MermaidTokens.ATTRIBUTE_WORD, MermaidTokens.ATTRIBUTE_WORD).spaces(1)
      // State diagram
      .after(MermaidTokens.StateDiagram.STATE).spaces(1)
      .after(MermaidElements.SPECIAL_STATE).spaces(1)
      .after(MermaidTokens.ARROW).spaces(1)
      .between(MermaidTokens.NOTE_CONTENT, MermaidTokens.NOTE_CONTENT).spaces(1)
      .between(MermaidTokens.LABEL, MermaidTokens.LABEL).spaces(1)
      // Entity Relationship
      .around(MermaidElements.RELATIONSHIP).spaces(1)
      .after(MermaidTokens.EntityRelationship.ATTR_KEY).spaces(1)
      .before(MermaidElements.ATTR_NAME).spaces(1)
      .between(MermaidElements.ATTR_NAME, MermaidTokens.EntityRelationship.ATTR_KEY).spaces(1)
      .between(MermaidElements.ATTR_NAME, MermaidElements.STRING).spaces(1)
      // User Journey
      .after(MermaidTokens.TASK_NAME).spaces(1)
      .after(MermaidTokens.SECTION).spaces(1)
      .after(MermaidTokens.TITLE).spaces(1)
      .between(MermaidTokens.TASK_NAME, MermaidTokens.TASK_NAME).spaces(1)
      .between(MermaidTokens.TASK_DATA, MermaidTokens.TASK_DATA).spaces(1)
      .between(MermaidTokens.SECTION_TITLE, MermaidTokens.SECTION_TITLE).spaces(1)
      // Pie
      .before(MermaidTokens.Pie.SHOW_DATA).spaces(1)
      // Requirement
      .after(MermaidElements.REQUIREMENT_TYPE).spaces(1)
      .after(MermaidTokens.Requirement.ELEMENT).spaces(1)
      .around(MermaidTokens.Requirement.ARROW_LEFT).spaces(1)
      .around(MermaidTokens.Requirement.ARROW_RIGHT).spaces(1)
      .around(MermaidTokens.Requirement.REQ_LINE).spaces(1)
      // Git  Graph
      .after(MermaidTokens.GitGraph.BRANCH).spaces(1)
      .after(MermaidTokens.GitGraph.CHERRY_PICK).spaces(1)
      .after(MermaidTokens.GitGraph.MERGE).spaces(1)
      .after(MermaidTokens.GitGraph.CHECKOUT).spaces(1)
      .after(MermaidTokens.GitGraph.COMMIT).spaces(1)
      .around(MermaidElements.COMMIT_ID_ATTRIBUTE).spaces(1)
      .around(MermaidElements.COMMIT_TAG_ATTRIBUTE).spaces(1)
      .around(MermaidElements.COMMIT_TYPE_ATTRIBUTE).spaces(1)
      .around(MermaidElements.BRANCH_ORDER).spaces(1)
      // C4
      .after(C4_KEYWORDS_SPACE_AFTER).spaceIf(false)
      .around(MermaidTokens.C4.EQUALITY).spacing(0, 1, 0, false, 0)
      // indent
      .between(MermaidElements.SEQUENCE_DOCUMENT, MermaidTokens.END).spaces(indentOptions.INDENT_SIZE)
      .between(MermaidElements.SUBGRAPH_DOCUMENT, MermaidTokens.END).spaces(indentOptions.INDENT_SIZE)
      .between(MermaidElements.COMPLEX_NOTE_CONTENT, MermaidTokens.END).spaces(indentOptions.INDENT_SIZE)
  }
}
