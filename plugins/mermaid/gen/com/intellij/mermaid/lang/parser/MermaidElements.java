// This is a generated file. Not intended for manual editing.
package com.intellij.mermaid.lang.parser;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.mermaid.lang.psi.MermaidElementType;
import com.intellij.mermaid.lang.lexer.MermaidToken;
import com.intellij.mermaid.lang.psi.impl.*;

public interface MermaidElements {

  IElementType ACC_DESCR_MULTILINE_VALUE_LINES = new MermaidElementType("ACC_DESCR_MULTILINE_VALUE_LINES");
  IElementType ACC_STATEMENT = new MermaidElementType("ACC_STATEMENT");
  IElementType ACTIVATE_STATEMENT = new MermaidElementType("ACTIVATE_STATEMENT");
  IElementType ACTOR_STATEMENT = new MermaidElementType("ACTOR_STATEMENT");
  IElementType ALT_HEADER = new MermaidElementType("ALT_HEADER");
  IElementType ALT_STATEMENT = new MermaidElementType("ALT_STATEMENT");
  IElementType AND_HEADER = new MermaidElementType("AND_HEADER");
  IElementType ANNOTATION = new MermaidElementType("ANNOTATION");
  IElementType ANNOTATION_STATEMENT = new MermaidElementType("ANNOTATION_STATEMENT");
  IElementType ARROW_DATA = new MermaidElementType("ARROW_DATA");
  IElementType ATTRIBUTE = new MermaidElementType("ATTRIBUTE");
  IElementType ATTR_KEYS = new MermaidElementType("ATTR_KEYS");
  IElementType ATTR_NAME = new MermaidElementType("ATTR_NAME");
  IElementType ATTR_TYPE = new MermaidElementType("ATTR_TYPE");
  IElementType ATTR_TYPE_WITH_GENERIC = new MermaidElementType("ATTR_TYPE_WITH_GENERIC");
  IElementType AUTONUMBER_STATEMENT = new MermaidElementType("AUTONUMBER_STATEMENT");
  IElementType AXIS_DETAILS_STATEMENT = new MermaidElementType("AXIS_DETAILS_STATEMENT");
  IElementType BAND_DATA = new MermaidElementType("BAND_DATA");
  IElementType BAR_STATEMENT = new MermaidElementType("BAR_STATEMENT");
  IElementType BLOCK_DIAGRAM_ARROW = new MermaidElementType("BLOCK_DIAGRAM_ARROW");
  IElementType BLOCK_DIAGRAM_BODY = new MermaidElementType("BLOCK_DIAGRAM_BODY");
  IElementType BLOCK_DIAGRAM_COMPLEX_STATEMENT = new MermaidElementType("BLOCK_DIAGRAM_COMPLEX_STATEMENT");
  IElementType BLOCK_DIAGRAM_HEADER = new MermaidElementType("BLOCK_DIAGRAM_HEADER");
  IElementType BLOCK_DIAGRAM_NODE = new MermaidElementType("BLOCK_DIAGRAM_NODE");
  IElementType BLOCK_DIAGRAM_NODE_DESCR = new MermaidElementType("BLOCK_DIAGRAM_NODE_DESCR");
  IElementType BLOCK_DIAGRAM_NODE_STATEMENT = new MermaidElementType("BLOCK_DIAGRAM_NODE_STATEMENT");
  IElementType BLOCK_DIAGRAM_NODE_STATEMENT_INNER = new MermaidElementType("BLOCK_DIAGRAM_NODE_STATEMENT_INNER");
  IElementType BLOCK_SIZE = new MermaidElementType("BLOCK_SIZE");
  IElementType BLOCK_STATEMENT = new MermaidElementType("BLOCK_STATEMENT");
  IElementType BLOCK_STATEMENT_HEADER = new MermaidElementType("BLOCK_STATEMENT_HEADER");
  IElementType BOUNDARY_BLOCK = new MermaidElementType("BOUNDARY_BLOCK");
  IElementType BOUNDARY_HEADER = new MermaidElementType("BOUNDARY_HEADER");
  IElementType BOUNDARY_STATEMENT = new MermaidElementType("BOUNDARY_STATEMENT");
  IElementType BOX_BLOCK = new MermaidElementType("BOX_BLOCK");
  IElementType BOX_HEADER = new MermaidElementType("BOX_HEADER");
  IElementType BOX_STATEMENT = new MermaidElementType("BOX_STATEMENT");
  IElementType BRANCH_ORDER = new MermaidElementType("BRANCH_ORDER");
  IElementType BRANCH_STATEMENT = new MermaidElementType("BRANCH_STATEMENT");
  IElementType BREAK_HEADER = new MermaidElementType("BREAK_HEADER");
  IElementType BREAK_STATEMENT = new MermaidElementType("BREAK_STATEMENT");
  IElementType CALLBACK_ARGS = new MermaidElementType("CALLBACK_ARGS");
  IElementType CARDINALITY = new MermaidElementType("CARDINALITY");
  IElementType CHECKOUT_STATEMENT = new MermaidElementType("CHECKOUT_STATEMENT");
  IElementType CHERRY_PICK_STATEMENT = new MermaidElementType("CHERRY_PICK_STATEMENT");
  IElementType CLASS_BLOCK = new MermaidElementType("CLASS_BLOCK");
  IElementType CLASS_BODY = new MermaidElementType("CLASS_BODY");
  IElementType CLASS_DEF_STATEMENT = new MermaidElementType("CLASS_DEF_STATEMENT");
  IElementType CLASS_DIAGRAM_CLICK_STATEMENT = new MermaidElementType("CLASS_DIAGRAM_CLICK_STATEMENT");
  IElementType CLASS_DIAGRAM_HEADER = new MermaidElementType("CLASS_DIAGRAM_HEADER");
  IElementType CLASS_DIAGRAM_IDENTIFIER = new MermaidElementType("CLASS_DIAGRAM_IDENTIFIER");
  IElementType CLASS_DIAGRAM_NOTE_STATEMENT = new MermaidElementType("CLASS_DIAGRAM_NOTE_STATEMENT");
  IElementType CLASS_DIAGRAM_NOTE_TEXT = new MermaidElementType("CLASS_DIAGRAM_NOTE_TEXT");
  IElementType CLASS_HEADER = new MermaidElementType("CLASS_HEADER");
  IElementType CLASS_LABEL = new MermaidElementType("CLASS_LABEL");
  IElementType CLASS_STATEMENT = new MermaidElementType("CLASS_STATEMENT");
  IElementType COLUMNS_STATEMENT = new MermaidElementType("COLUMNS_STATEMENT");
  IElementType COMMIT_ARG = new MermaidElementType("COMMIT_ARG");
  IElementType COMMIT_ID_ATTRIBUTE = new MermaidElementType("COMMIT_ID_ATTRIBUTE");
  IElementType COMMIT_ID_VALUE = new MermaidElementType("COMMIT_ID_VALUE");
  IElementType COMMIT_MSG_ATTRIBUTE = new MermaidElementType("COMMIT_MSG_ATTRIBUTE");
  IElementType COMMIT_STATEMENT = new MermaidElementType("COMMIT_STATEMENT");
  IElementType COMMIT_TAG_ATTRIBUTE = new MermaidElementType("COMMIT_TAG_ATTRIBUTE");
  IElementType COMMIT_TYPE_ATTRIBUTE = new MermaidElementType("COMMIT_TYPE_ATTRIBUTE");
  IElementType COMPLEX_ACC_DESCR_MULTILINE_VALUE = new MermaidElementType("COMPLEX_ACC_DESCR_MULTILINE_VALUE");
  IElementType COMPLEX_ACC_DESCR_VALUE = new MermaidElementType("COMPLEX_ACC_DESCR_VALUE");
  IElementType COMPLEX_ACC_TITLE_VALUE = new MermaidElementType("COMPLEX_ACC_TITLE_VALUE");
  IElementType COMPLEX_CONTROL_ID = new MermaidElementType("COMPLEX_CONTROL_ID");
  IElementType COMPLEX_IDENTIFIER = new MermaidElementType("COMPLEX_IDENTIFIER");
  IElementType COMPLEX_LABEL = new MermaidElementType("COMPLEX_LABEL");
  IElementType COMPLEX_LINK_TEXT = new MermaidElementType("COMPLEX_LINK_TEXT");
  IElementType COMPLEX_MESSAGE = new MermaidElementType("COMPLEX_MESSAGE");
  IElementType COMPLEX_NAMED_DATA = new MermaidElementType("COMPLEX_NAMED_DATA");
  IElementType COMPLEX_NOTE_CONTENT = new MermaidElementType("COMPLEX_NOTE_CONTENT");
  IElementType COMPLEX_SANKEY_TEXT = new MermaidElementType("COMPLEX_SANKEY_TEXT");
  IElementType COMPLEX_SECTION_TITLE = new MermaidElementType("COMPLEX_SECTION_TITLE");
  IElementType COMPLEX_TASK_DATA = new MermaidElementType("COMPLEX_TASK_DATA");
  IElementType COMPLEX_TASK_NAME = new MermaidElementType("COMPLEX_TASK_NAME");
  IElementType COMPLEX_TITLE_VALUE = new MermaidElementType("COMPLEX_TITLE_VALUE");
  IElementType COMPOSITE_STATE_DECLARATION = new MermaidElementType("COMPOSITE_STATE_DECLARATION");
  IElementType CRITICAL_HEADER = new MermaidElementType("CRITICAL_HEADER");
  IElementType CRITICAL_STATEMENT = new MermaidElementType("CRITICAL_STATEMENT");
  IElementType CSS_CLASS_STATEMENT = new MermaidElementType("CSS_CLASS_STATEMENT");
  IElementType C_4_ATTR = new MermaidElementType("C_4_ATTR");
  IElementType C_4_ATTRIBUTES = new MermaidElementType("C_4_ATTRIBUTES");
  IElementType C_4_ATTR_COMPLEX_NAME = new MermaidElementType("C_4_ATTR_COMPLEX_NAME");
  IElementType C_4_BODY = new MermaidElementType("C_4_BODY");
  IElementType C_4_COMPONENT_STATEMENT = new MermaidElementType("C_4_COMPONENT_STATEMENT");
  IElementType C_4_HEADER = new MermaidElementType("C_4_HEADER");
  IElementType DEACTIVATE_STATEMENT = new MermaidElementType("DEACTIVATE_STATEMENT");
  IElementType DESCRIPTION = new MermaidElementType("DESCRIPTION");
  IElementType DIRECTIONS = new MermaidElementType("DIRECTIONS");
  IElementType DIRECTION_STATEMENT = new MermaidElementType("DIRECTION_STATEMENT");
  IElementType DIRECTIVE = new MermaidElementType("DIRECTIVE");
  IElementType DIVIDER_STATEMENT = new MermaidElementType("DIVIDER_STATEMENT");
  IElementType ELEMENT_BLOCK = new MermaidElementType("ELEMENT_BLOCK");
  IElementType ELEMENT_DEF = new MermaidElementType("ELEMENT_DEF");
  IElementType ELEMENT_DOC_REF_ATTRIBUTE = new MermaidElementType("ELEMENT_DOC_REF_ATTRIBUTE");
  IElementType ELEMENT_HEADER = new MermaidElementType("ELEMENT_HEADER");
  IElementType ELEMENT_TYPE_ATTRIBUTE = new MermaidElementType("ELEMENT_TYPE_ATTRIBUTE");
  IElementType ELSE_HEADER = new MermaidElementType("ELSE_HEADER");
  IElementType ENTITY_DECLARATION = new MermaidElementType("ENTITY_DECLARATION");
  IElementType ER_ATTRIBUTE = new MermaidElementType("ER_ATTRIBUTE");
  IElementType ER_BODY = new MermaidElementType("ER_BODY");
  IElementType ER_ENTITY_BLOCK = new MermaidElementType("ER_ENTITY_BLOCK");
  IElementType ER_HEADER = new MermaidElementType("ER_HEADER");
  IElementType ER_IDENTIFIER = new MermaidElementType("ER_IDENTIFIER");
  IElementType ER_IDENTIFIER_ALIAS = new MermaidElementType("ER_IDENTIFIER_ALIAS");
  IElementType ER_RELATION_STATEMENT = new MermaidElementType("ER_RELATION_STATEMENT");
  IElementType FLOWCHART_BODY = new MermaidElementType("FLOWCHART_BODY");
  IElementType FLOWCHART_CLASS_STATEMENT = new MermaidElementType("FLOWCHART_CLASS_STATEMENT");
  IElementType FLOWCHART_CLICK_STATEMENT = new MermaidElementType("FLOWCHART_CLICK_STATEMENT");
  IElementType FLOWCHART_HEADER = new MermaidElementType("FLOWCHART_HEADER");
  IElementType FLOWCHART_LINK_STATEMENT = new MermaidElementType("FLOWCHART_LINK_STATEMENT");
  IElementType FRONTMATTER = new MermaidElementType("FRONTMATTER");
  IElementType GANTT_AXIS_FORMAT_STATEMENT = new MermaidElementType("GANTT_AXIS_FORMAT_STATEMENT");
  IElementType GANTT_BODY = new MermaidElementType("GANTT_BODY");
  IElementType GANTT_CLICK_STATEMENT = new MermaidElementType("GANTT_CLICK_STATEMENT");
  IElementType GANTT_DATA_STATEMENT = new MermaidElementType("GANTT_DATA_STATEMENT");
  IElementType GANTT_DATE_FORMAT_STATEMENT = new MermaidElementType("GANTT_DATE_FORMAT_STATEMENT");
  IElementType GANTT_EXCLUDES_STATEMENT = new MermaidElementType("GANTT_EXCLUDES_STATEMENT");
  IElementType GANTT_HEADER = new MermaidElementType("GANTT_HEADER");
  IElementType GANTT_INCLUDES_STATEMENT = new MermaidElementType("GANTT_INCLUDES_STATEMENT");
  IElementType GANTT_INCLUSIVE_END_DATES_STATEMENT = new MermaidElementType("GANTT_INCLUSIVE_END_DATES_STATEMENT");
  IElementType GANTT_SECTION_BLOCK = new MermaidElementType("GANTT_SECTION_BLOCK");
  IElementType GANTT_SECTION_HEADER = new MermaidElementType("GANTT_SECTION_HEADER");
  IElementType GANTT_SECTION_STATEMENT = new MermaidElementType("GANTT_SECTION_STATEMENT");
  IElementType GANTT_TICK_INTERVAL_STATEMENT = new MermaidElementType("GANTT_TICK_INTERVAL_STATEMENT");
  IElementType GANTT_TODAY_MARKER_STATEMENT = new MermaidElementType("GANTT_TODAY_MARKER_STATEMENT");
  IElementType GANTT_TOP_AXIS_STATEMENT = new MermaidElementType("GANTT_TOP_AXIS_STATEMENT");
  IElementType GANTT_WEEKDAY_STATEMENT = new MermaidElementType("GANTT_WEEKDAY_STATEMENT");
  IElementType GENERIC = new MermaidElementType("GENERIC");
  IElementType GENERIC_TYPE_ID = new MermaidElementType("GENERIC_TYPE_ID");
  IElementType GIT_GRAPH_BODY = new MermaidElementType("GIT_GRAPH_BODY");
  IElementType GIT_GRAPH_BRANCH_IDENTIFIER = new MermaidElementType("GIT_GRAPH_BRANCH_IDENTIFIER");
  IElementType GIT_GRAPH_HEADER = new MermaidElementType("GIT_GRAPH_HEADER");
  IElementType ICON_STATEMENT = new MermaidElementType("ICON_STATEMENT");
  IElementType IDENTIFIER = new MermaidElementType("IDENTIFIER");
  IElementType IDENTIFYING_COMPLEX_SANKEY_TEXT = new MermaidElementType("IDENTIFYING_COMPLEX_SANKEY_TEXT");
  IElementType IDENTIFYING_QUOTED_SANKEY_FIELD = new MermaidElementType("IDENTIFYING_QUOTED_SANKEY_FIELD");
  IElementType IDENTIFYING_QUOTED_SANKEY_FIELD_VALUE = new MermaidElementType("IDENTIFYING_QUOTED_SANKEY_FIELD_VALUE");
  IElementType ID_ALIAS = new MermaidElementType("ID_ALIAS");
  IElementType JOURNEY_BODY = new MermaidElementType("JOURNEY_BODY");
  IElementType JOURNEY_DATA_STATEMENT = new MermaidElementType("JOURNEY_DATA_STATEMENT");
  IElementType JOURNEY_HEADER = new MermaidElementType("JOURNEY_HEADER");
  IElementType JOURNEY_NAMED_DATA = new MermaidElementType("JOURNEY_NAMED_DATA");
  IElementType JOURNEY_SECTION_BLOCK = new MermaidElementType("JOURNEY_SECTION_BLOCK");
  IElementType JOURNEY_SECTION_HEADER = new MermaidElementType("JOURNEY_SECTION_HEADER");
  IElementType JOURNEY_SECTION_STATEMENT = new MermaidElementType("JOURNEY_SECTION_STATEMENT");
  IElementType LEFT_ID = new MermaidElementType("LEFT_ID");
  IElementType LINE_STATEMENT = new MermaidElementType("LINE_STATEMENT");
  IElementType LINE_TYPE = new MermaidElementType("LINE_TYPE");
  IElementType LINKS_STATEMENT = new MermaidElementType("LINKS_STATEMENT");
  IElementType LINKS_VALUES = new MermaidElementType("LINKS_VALUES");
  IElementType LINK_STATEMENT = new MermaidElementType("LINK_STATEMENT");
  IElementType LINK_STYLE_STATEMENT = new MermaidElementType("LINK_STYLE_STATEMENT");
  IElementType LOOP_HEADER = new MermaidElementType("LOOP_HEADER");
  IElementType LOOP_STATEMENT = new MermaidElementType("LOOP_STATEMENT");
  IElementType MARKDOWN_VALUE = new MermaidElementType("MARKDOWN_VALUE");
  IElementType MEMBER_ATTRIBUTE = new MermaidElementType("MEMBER_ATTRIBUTE");
  IElementType MEMBER_STATEMENT = new MermaidElementType("MEMBER_STATEMENT");
  IElementType MERGE_STATEMENT = new MermaidElementType("MERGE_STATEMENT");
  IElementType MINDMAP_BODY = new MermaidElementType("MINDMAP_BODY");
  IElementType MINDMAP_CLASS_STATEMENT = new MermaidElementType("MINDMAP_CLASS_STATEMENT");
  IElementType MINDMAP_HEADER = new MermaidElementType("MINDMAP_HEADER");
  IElementType MINDMAP_NODE_DESCR = new MermaidElementType("MINDMAP_NODE_DESCR");
  IElementType MINDMAP_NODE_ID = new MermaidElementType("MINDMAP_NODE_ID");
  IElementType MINDMAP_NODE_STATEMENT = new MermaidElementType("MINDMAP_NODE_STATEMENT");
  IElementType NAMESPACE_BLOCK = new MermaidElementType("NAMESPACE_BLOCK");
  IElementType NAMESPACE_HEADER = new MermaidElementType("NAMESPACE_HEADER");
  IElementType NAMESPACE_IDENTIFIER = new MermaidElementType("NAMESPACE_IDENTIFIER");
  IElementType NAMESPACE_STATEMENT = new MermaidElementType("NAMESPACE_STATEMENT");
  IElementType NODE_DESCRIPTION = new MermaidElementType("NODE_DESCRIPTION");
  IElementType NODE_STATEMENT = new MermaidElementType("NODE_STATEMENT");
  IElementType NODE_TEXT = new MermaidElementType("NODE_TEXT");
  IElementType NOTE_HEADER = new MermaidElementType("NOTE_HEADER");
  IElementType NOTE_STATEMENT = new MermaidElementType("NOTE_STATEMENT");
  IElementType OPTION_HEADER = new MermaidElementType("OPTION_HEADER");
  IElementType OPT_HEADER = new MermaidElementType("OPT_HEADER");
  IElementType OPT_STATEMENT = new MermaidElementType("OPT_STATEMENT");
  IElementType PARENT_COMMIT_ID_ATTRIBUTE = new MermaidElementType("PARENT_COMMIT_ID_ATTRIBUTE");
  IElementType PAR_HEADER = new MermaidElementType("PAR_HEADER");
  IElementType PAR_OVER_HEADER = new MermaidElementType("PAR_OVER_HEADER");
  IElementType PAR_OVER_STATEMENT = new MermaidElementType("PAR_OVER_STATEMENT");
  IElementType PAR_STATEMENT = new MermaidElementType("PAR_STATEMENT");
  IElementType PIE_BODY = new MermaidElementType("PIE_BODY");
  IElementType PIE_DATA_STATEMENT = new MermaidElementType("PIE_DATA_STATEMENT");
  IElementType PIE_HEADER = new MermaidElementType("PIE_HEADER");
  IElementType PLOT_DATA = new MermaidElementType("PLOT_DATA");
  IElementType POINT = new MermaidElementType("POINT");
  IElementType POINT_STATEMENT = new MermaidElementType("POINT_STATEMENT");
  IElementType QUADRANT_BODY = new MermaidElementType("QUADRANT_BODY");
  IElementType QUADRANT_COMPLEX_TEXT = new MermaidElementType("QUADRANT_COMPLEX_TEXT");
  IElementType QUADRANT_DETAILS_STATEMENT = new MermaidElementType("QUADRANT_DETAILS_STATEMENT");
  IElementType QUADRANT_HEADER = new MermaidElementType("QUADRANT_HEADER");
  IElementType QUOTED_BRANCH_IDENTIFIER = new MermaidElementType("QUOTED_BRANCH_IDENTIFIER");
  IElementType QUOTED_CLASS_IDENTIFIER = new MermaidElementType("QUOTED_CLASS_IDENTIFIER");
  IElementType QUOTED_SANKEY_FIELD = new MermaidElementType("QUOTED_SANKEY_FIELD");
  IElementType QUOTED_SANKEY_FIELD_INNER_VALUE = new MermaidElementType("QUOTED_SANKEY_FIELD_INNER_VALUE");
  IElementType QUOTED_SANKEY_FIELD_VALUE = new MermaidElementType("QUOTED_SANKEY_FIELD_VALUE");
  IElementType RECT_HEADER = new MermaidElementType("RECT_HEADER");
  IElementType RECT_STATEMENT = new MermaidElementType("RECT_STATEMENT");
  IElementType RELATION = new MermaidElementType("RELATION");
  IElementType RELATIONSHIP = new MermaidElementType("RELATIONSHIP");
  IElementType RELATIONSHIP_DEF = new MermaidElementType("RELATIONSHIP_DEF");
  IElementType RELATION_STATEMENT = new MermaidElementType("RELATION_STATEMENT");
  IElementType RELATION_TYPE_LEFT = new MermaidElementType("RELATION_TYPE_LEFT");
  IElementType RELATION_TYPE_RIGHT = new MermaidElementType("RELATION_TYPE_RIGHT");
  IElementType REQUIREMENT_BLOCK = new MermaidElementType("REQUIREMENT_BLOCK");
  IElementType REQUIREMENT_DEF = new MermaidElementType("REQUIREMENT_DEF");
  IElementType REQUIREMENT_DIAGRAM_BODY = new MermaidElementType("REQUIREMENT_DIAGRAM_BODY");
  IElementType REQUIREMENT_DIAGRAM_HEADER = new MermaidElementType("REQUIREMENT_DIAGRAM_HEADER");
  IElementType REQUIREMENT_HEADER = new MermaidElementType("REQUIREMENT_HEADER");
  IElementType REQUIREMENT_ID_ATTRIBUTE = new MermaidElementType("REQUIREMENT_ID_ATTRIBUTE");
  IElementType REQUIREMENT_RISK_ATTRIBUTE = new MermaidElementType("REQUIREMENT_RISK_ATTRIBUTE");
  IElementType REQUIREMENT_TEXT_ATTRIBUTE = new MermaidElementType("REQUIREMENT_TEXT_ATTRIBUTE");
  IElementType REQUIREMENT_TYPE = new MermaidElementType("REQUIREMENT_TYPE");
  IElementType REQUIREMENT_VERIFY_METHOD_ATTRIBUTE = new MermaidElementType("REQUIREMENT_VERIFY_METHOD_ATTRIBUTE");
  IElementType REQ_RELATIONSHIP = new MermaidElementType("REQ_RELATIONSHIP");
  IElementType RIGHT_ID = new MermaidElementType("RIGHT_ID");
  IElementType RISK_LEVEL = new MermaidElementType("RISK_LEVEL");
  IElementType SANKEY_BODY = new MermaidElementType("SANKEY_BODY");
  IElementType SANKEY_HEADER = new MermaidElementType("SANKEY_HEADER");
  IElementType SANKEY_RECORD_STATEMENT = new MermaidElementType("SANKEY_RECORD_STATEMENT");
  IElementType SECTION_TASK_DATA = new MermaidElementType("SECTION_TASK_DATA");
  IElementType SEQUENCE_BODY = new MermaidElementType("SEQUENCE_BODY");
  IElementType SEQUENCE_HEADER = new MermaidElementType("SEQUENCE_HEADER");
  IElementType SIGNAL = new MermaidElementType("SIGNAL");
  IElementType SIGNAL_STATEMENT = new MermaidElementType("SIGNAL_STATEMENT");
  IElementType SIGNAL_TYPE = new MermaidElementType("SIGNAL_TYPE");
  IElementType SIMPLE_NOTE_CONTENT = new MermaidElementType("SIMPLE_NOTE_CONTENT");
  IElementType SPACE_STATEMENT = new MermaidElementType("SPACE_STATEMENT");
  IElementType SPACE_STATEMENT_INNER = new MermaidElementType("SPACE_STATEMENT_INNER");
  IElementType SPECIAL_STATE = new MermaidElementType("SPECIAL_STATE");
  IElementType STATE_BLOCK = new MermaidElementType("STATE_BLOCK");
  IElementType STATE_BODY = new MermaidElementType("STATE_BODY");
  IElementType STATE_CLASS_DEF_STATEMENT = new MermaidElementType("STATE_CLASS_DEF_STATEMENT");
  IElementType STATE_DECLARATION = new MermaidElementType("STATE_DECLARATION");
  IElementType STATE_DECLARATION_HEADER = new MermaidElementType("STATE_DECLARATION_HEADER");
  IElementType STATE_HEADER = new MermaidElementType("STATE_HEADER");
  IElementType STATE_ID = new MermaidElementType("STATE_ID");
  IElementType STATE_NOTE = new MermaidElementType("STATE_NOTE");
  IElementType STATE_RELATION_STATEMENT = new MermaidElementType("STATE_RELATION_STATEMENT");
  IElementType STRING = new MermaidElementType("STRING");
  IElementType STYLED_VERTEX = new MermaidElementType("STYLED_VERTEX");
  IElementType STYLE_OPTIONS = new MermaidElementType("STYLE_OPTIONS");
  IElementType STYLE_STATEMENT = new MermaidElementType("STYLE_STATEMENT");
  IElementType STYLE_STATEMENT_TARGET = new MermaidElementType("STYLE_STATEMENT_TARGET");
  IElementType SUBGRAPH_BLOCK = new MermaidElementType("SUBGRAPH_BLOCK");
  IElementType SUBGRAPH_HEADER = new MermaidElementType("SUBGRAPH_HEADER");
  IElementType SUBGRAPH_NAME = new MermaidElementType("SUBGRAPH_NAME");
  IElementType SUBGRAPH_STATEMENT = new MermaidElementType("SUBGRAPH_STATEMENT");
  IElementType TIMELINE_BODY = new MermaidElementType("TIMELINE_BODY");
  IElementType TIMELINE_DATA_STATEMENT = new MermaidElementType("TIMELINE_DATA_STATEMENT");
  IElementType TIMELINE_HEADER = new MermaidElementType("TIMELINE_HEADER");
  IElementType TIMELINE_SECTION_BLOCK = new MermaidElementType("TIMELINE_SECTION_BLOCK");
  IElementType TIMELINE_SECTION_HEADER = new MermaidElementType("TIMELINE_SECTION_HEADER");
  IElementType TIMELINE_SECTION_STATEMENT = new MermaidElementType("TIMELINE_SECTION_STATEMENT");
  IElementType TITLE_STATEMENT = new MermaidElementType("TITLE_STATEMENT");
  IElementType VERIFY_TYPE = new MermaidElementType("VERIFY_TYPE");
  IElementType VERTEX = new MermaidElementType("VERTEX");
  IElementType VERTEX_STATEMENT = new MermaidElementType("VERTEX_STATEMENT");
  IElementType VERTEX_TEXT = new MermaidElementType("VERTEX_TEXT");
  IElementType XY_CHART_BODY = new MermaidElementType("XY_CHART_BODY");
  IElementType XY_CHART_HEADER = new MermaidElementType("XY_CHART_HEADER");
  IElementType X_AXIS_STATEMENT = new MermaidElementType("X_AXIS_STATEMENT");
  IElementType Y_AXIS_STATEMENT = new MermaidElementType("Y_AXIS_STATEMENT");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == ACC_DESCR_MULTILINE_VALUE_LINES) {
        return new MermaidAccDescrMultilineValueLinesImpl(node);
      }
      else if (type == ACC_STATEMENT) {
        return new MermaidAccStatementImpl(node);
      }
      else if (type == ACTIVATE_STATEMENT) {
        return new MermaidActivateStatementImpl(node);
      }
      else if (type == ACTOR_STATEMENT) {
        return new MermaidActorStatementImpl(node);
      }
      else if (type == ALT_HEADER) {
        return new MermaidAltHeaderImpl(node);
      }
      else if (type == ALT_STATEMENT) {
        return new MermaidAltStatementImpl(node);
      }
      else if (type == AND_HEADER) {
        return new MermaidAndHeaderImpl(node);
      }
      else if (type == ANNOTATION) {
        return new MermaidAnnotationImpl(node);
      }
      else if (type == ANNOTATION_STATEMENT) {
        return new MermaidAnnotationStatementImpl(node);
      }
      else if (type == ARROW_DATA) {
        return new MermaidArrowDataImpl(node);
      }
      else if (type == ATTRIBUTE) {
        return new MermaidAttributeImpl(node);
      }
      else if (type == ATTR_KEYS) {
        return new MermaidAttrKeysImpl(node);
      }
      else if (type == ATTR_NAME) {
        return new MermaidAttrNameImpl(node);
      }
      else if (type == ATTR_TYPE) {
        return new MermaidAttrTypeImpl(node);
      }
      else if (type == ATTR_TYPE_WITH_GENERIC) {
        return new MermaidAttrTypeWithGenericImpl(node);
      }
      else if (type == AUTONUMBER_STATEMENT) {
        return new MermaidAutonumberStatementImpl(node);
      }
      else if (type == AXIS_DETAILS_STATEMENT) {
        return new MermaidAxisDetailsStatementImpl(node);
      }
      else if (type == BAND_DATA) {
        return new MermaidBandDataImpl(node);
      }
      else if (type == BAR_STATEMENT) {
        return new MermaidBarStatementImpl(node);
      }
      else if (type == BLOCK_DIAGRAM_ARROW) {
        return new MermaidBlockDiagramArrowImpl(node);
      }
      else if (type == BLOCK_DIAGRAM_BODY) {
        return new MermaidBlockDiagramBodyImpl(node);
      }
      else if (type == BLOCK_DIAGRAM_COMPLEX_STATEMENT) {
        return new MermaidBlockDiagramComplexStatementImpl(node);
      }
      else if (type == BLOCK_DIAGRAM_HEADER) {
        return new MermaidBlockDiagramHeaderImpl(node);
      }
      else if (type == BLOCK_DIAGRAM_NODE) {
        return new MermaidBlockDiagramNodeImpl(node);
      }
      else if (type == BLOCK_DIAGRAM_NODE_DESCR) {
        return new MermaidBlockDiagramNodeDescrImpl(node);
      }
      else if (type == BLOCK_DIAGRAM_NODE_STATEMENT) {
        return new MermaidBlockDiagramNodeStatementImpl(node);
      }
      else if (type == BLOCK_DIAGRAM_NODE_STATEMENT_INNER) {
        return new MermaidBlockDiagramNodeStatementInnerImpl(node);
      }
      else if (type == BLOCK_SIZE) {
        return new MermaidBlockSizeImpl(node);
      }
      else if (type == BLOCK_STATEMENT) {
        return new MermaidBlockStatementImpl(node);
      }
      else if (type == BLOCK_STATEMENT_HEADER) {
        return new MermaidBlockStatementHeaderImpl(node);
      }
      else if (type == BOUNDARY_BLOCK) {
        return new MermaidBoundaryBlockImpl(node);
      }
      else if (type == BOUNDARY_HEADER) {
        return new MermaidBoundaryHeaderImpl(node);
      }
      else if (type == BOUNDARY_STATEMENT) {
        return new MermaidBoundaryStatementImpl(node);
      }
      else if (type == BOX_BLOCK) {
        return new MermaidBoxBlockImpl(node);
      }
      else if (type == BOX_HEADER) {
        return new MermaidBoxHeaderImpl(node);
      }
      else if (type == BOX_STATEMENT) {
        return new MermaidBoxStatementImpl(node);
      }
      else if (type == BRANCH_ORDER) {
        return new MermaidBranchOrderImpl(node);
      }
      else if (type == BRANCH_STATEMENT) {
        return new MermaidBranchStatementImpl(node);
      }
      else if (type == BREAK_HEADER) {
        return new MermaidBreakHeaderImpl(node);
      }
      else if (type == BREAK_STATEMENT) {
        return new MermaidBreakStatementImpl(node);
      }
      else if (type == CALLBACK_ARGS) {
        return new MermaidCallbackArgsImpl(node);
      }
      else if (type == CARDINALITY) {
        return new MermaidCardinalityImpl(node);
      }
      else if (type == CHECKOUT_STATEMENT) {
        return new MermaidCheckoutStatementImpl(node);
      }
      else if (type == CHERRY_PICK_STATEMENT) {
        return new MermaidCherryPickStatementImpl(node);
      }
      else if (type == CLASS_BLOCK) {
        return new MermaidClassBlockImpl(node);
      }
      else if (type == CLASS_BODY) {
        return new MermaidClassBodyImpl(node);
      }
      else if (type == CLASS_DEF_STATEMENT) {
        return new MermaidClassDefStatementImpl(node);
      }
      else if (type == CLASS_DIAGRAM_CLICK_STATEMENT) {
        return new MermaidClassDiagramClickStatementImpl(node);
      }
      else if (type == CLASS_DIAGRAM_HEADER) {
        return new MermaidClassDiagramHeaderImpl(node);
      }
      else if (type == CLASS_DIAGRAM_IDENTIFIER) {
        return new MermaidClassDiagramIdentifierImpl(node);
      }
      else if (type == CLASS_DIAGRAM_NOTE_STATEMENT) {
        return new MermaidClassDiagramNoteStatementImpl(node);
      }
      else if (type == CLASS_DIAGRAM_NOTE_TEXT) {
        return new MermaidClassDiagramNoteTextImpl(node);
      }
      else if (type == CLASS_HEADER) {
        return new MermaidClassHeaderImpl(node);
      }
      else if (type == CLASS_LABEL) {
        return new MermaidClassLabelImpl(node);
      }
      else if (type == CLASS_STATEMENT) {
        return new MermaidClassStatementImpl(node);
      }
      else if (type == COLUMNS_STATEMENT) {
        return new MermaidColumnsStatementImpl(node);
      }
      else if (type == COMMIT_ARG) {
        return new MermaidCommitArgImpl(node);
      }
      else if (type == COMMIT_ID_ATTRIBUTE) {
        return new MermaidCommitIdAttributeImpl(node);
      }
      else if (type == COMMIT_ID_VALUE) {
        return new MermaidCommitIdValueImpl(node);
      }
      else if (type == COMMIT_MSG_ATTRIBUTE) {
        return new MermaidCommitMsgAttributeImpl(node);
      }
      else if (type == COMMIT_STATEMENT) {
        return new MermaidCommitStatementImpl(node);
      }
      else if (type == COMMIT_TAG_ATTRIBUTE) {
        return new MermaidCommitTagAttributeImpl(node);
      }
      else if (type == COMMIT_TYPE_ATTRIBUTE) {
        return new MermaidCommitTypeAttributeImpl(node);
      }
      else if (type == COMPLEX_ACC_DESCR_MULTILINE_VALUE) {
        return new MermaidComplexAccDescrMultilineValueImpl(node);
      }
      else if (type == COMPLEX_ACC_DESCR_VALUE) {
        return new MermaidComplexAccDescrValueImpl(node);
      }
      else if (type == COMPLEX_ACC_TITLE_VALUE) {
        return new MermaidComplexAccTitleValueImpl(node);
      }
      else if (type == COMPLEX_CONTROL_ID) {
        return new MermaidComplexControlIdImpl(node);
      }
      else if (type == COMPLEX_IDENTIFIER) {
        return new MermaidComplexIdentifierImpl(node);
      }
      else if (type == COMPLEX_LABEL) {
        return new MermaidComplexLabelImpl(node);
      }
      else if (type == COMPLEX_LINK_TEXT) {
        return new MermaidComplexLinkTextImpl(node);
      }
      else if (type == COMPLEX_MESSAGE) {
        return new MermaidComplexMessageImpl(node);
      }
      else if (type == COMPLEX_NAMED_DATA) {
        return new MermaidComplexNamedDataImpl(node);
      }
      else if (type == COMPLEX_NOTE_CONTENT) {
        return new MermaidComplexNoteContentImpl(node);
      }
      else if (type == COMPLEX_SANKEY_TEXT) {
        return new MermaidComplexSankeyTextImpl(node);
      }
      else if (type == COMPLEX_SECTION_TITLE) {
        return new MermaidComplexSectionTitleImpl(node);
      }
      else if (type == COMPLEX_TASK_DATA) {
        return new MermaidComplexTaskDataImpl(node);
      }
      else if (type == COMPLEX_TASK_NAME) {
        return new MermaidComplexTaskNameImpl(node);
      }
      else if (type == COMPLEX_TITLE_VALUE) {
        return new MermaidComplexTitleValueImpl(node);
      }
      else if (type == COMPOSITE_STATE_DECLARATION) {
        return new MermaidCompositeStateDeclarationImpl(node);
      }
      else if (type == CRITICAL_HEADER) {
        return new MermaidCriticalHeaderImpl(node);
      }
      else if (type == CRITICAL_STATEMENT) {
        return new MermaidCriticalStatementImpl(node);
      }
      else if (type == CSS_CLASS_STATEMENT) {
        return new MermaidCssClassStatementImpl(node);
      }
      else if (type == C_4_ATTR) {
        return new MermaidC4AttrImpl(node);
      }
      else if (type == C_4_ATTRIBUTES) {
        return new MermaidC4AttributesImpl(node);
      }
      else if (type == C_4_ATTR_COMPLEX_NAME) {
        return new MermaidC4AttrComplexNameImpl(node);
      }
      else if (type == C_4_BODY) {
        return new MermaidC4BodyImpl(node);
      }
      else if (type == C_4_COMPONENT_STATEMENT) {
        return new MermaidC4ComponentStatementImpl(node);
      }
      else if (type == C_4_HEADER) {
        return new MermaidC4HeaderImpl(node);
      }
      else if (type == DEACTIVATE_STATEMENT) {
        return new MermaidDeactivateStatementImpl(node);
      }
      else if (type == DESCRIPTION) {
        return new MermaidDescriptionImpl(node);
      }
      else if (type == DIRECTIONS) {
        return new MermaidDirectionsImpl(node);
      }
      else if (type == DIRECTION_STATEMENT) {
        return new MermaidDirectionStatementImpl(node);
      }
      else if (type == DIRECTIVE) {
        return new MermaidDirectiveImpl(node);
      }
      else if (type == DIVIDER_STATEMENT) {
        return new MermaidDividerStatementImpl(node);
      }
      else if (type == ELEMENT_BLOCK) {
        return new MermaidElementBlockImpl(node);
      }
      else if (type == ELEMENT_DEF) {
        return new MermaidElementDefImpl(node);
      }
      else if (type == ELEMENT_DOC_REF_ATTRIBUTE) {
        return new MermaidElementDocRefAttributeImpl(node);
      }
      else if (type == ELEMENT_HEADER) {
        return new MermaidElementHeaderImpl(node);
      }
      else if (type == ELEMENT_TYPE_ATTRIBUTE) {
        return new MermaidElementTypeAttributeImpl(node);
      }
      else if (type == ELSE_HEADER) {
        return new MermaidElseHeaderImpl(node);
      }
      else if (type == ENTITY_DECLARATION) {
        return new MermaidEntityDeclarationImpl(node);
      }
      else if (type == ER_ATTRIBUTE) {
        return new MermaidErAttributeImpl(node);
      }
      else if (type == ER_BODY) {
        return new MermaidErBodyImpl(node);
      }
      else if (type == ER_ENTITY_BLOCK) {
        return new MermaidErEntityBlockImpl(node);
      }
      else if (type == ER_HEADER) {
        return new MermaidErHeaderImpl(node);
      }
      else if (type == ER_IDENTIFIER) {
        return new MermaidErIdentifierImpl(node);
      }
      else if (type == ER_IDENTIFIER_ALIAS) {
        return new MermaidErIdentifierAliasImpl(node);
      }
      else if (type == ER_RELATION_STATEMENT) {
        return new MermaidErRelationStatementImpl(node);
      }
      else if (type == FLOWCHART_BODY) {
        return new MermaidFlowchartBodyImpl(node);
      }
      else if (type == FLOWCHART_CLASS_STATEMENT) {
        return new MermaidFlowchartClassStatementImpl(node);
      }
      else if (type == FLOWCHART_CLICK_STATEMENT) {
        return new MermaidFlowchartClickStatementImpl(node);
      }
      else if (type == FLOWCHART_HEADER) {
        return new MermaidFlowchartHeaderImpl(node);
      }
      else if (type == FLOWCHART_LINK_STATEMENT) {
        return new MermaidFlowchartLinkStatementImpl(node);
      }
      else if (type == FRONTMATTER) {
        return new MermaidFrontmatterImpl(node);
      }
      else if (type == GANTT_AXIS_FORMAT_STATEMENT) {
        return new MermaidGanttAxisFormatStatementImpl(node);
      }
      else if (type == GANTT_BODY) {
        return new MermaidGanttBodyImpl(node);
      }
      else if (type == GANTT_CLICK_STATEMENT) {
        return new MermaidGanttClickStatementImpl(node);
      }
      else if (type == GANTT_DATA_STATEMENT) {
        return new MermaidGanttDataStatementImpl(node);
      }
      else if (type == GANTT_DATE_FORMAT_STATEMENT) {
        return new MermaidGanttDateFormatStatementImpl(node);
      }
      else if (type == GANTT_EXCLUDES_STATEMENT) {
        return new MermaidGanttExcludesStatementImpl(node);
      }
      else if (type == GANTT_HEADER) {
        return new MermaidGanttHeaderImpl(node);
      }
      else if (type == GANTT_INCLUDES_STATEMENT) {
        return new MermaidGanttIncludesStatementImpl(node);
      }
      else if (type == GANTT_INCLUSIVE_END_DATES_STATEMENT) {
        return new MermaidGanttInclusiveEndDatesStatementImpl(node);
      }
      else if (type == GANTT_SECTION_BLOCK) {
        return new MermaidGanttSectionBlockImpl(node);
      }
      else if (type == GANTT_SECTION_HEADER) {
        return new MermaidGanttSectionHeaderImpl(node);
      }
      else if (type == GANTT_SECTION_STATEMENT) {
        return new MermaidGanttSectionStatementImpl(node);
      }
      else if (type == GANTT_TICK_INTERVAL_STATEMENT) {
        return new MermaidGanttTickIntervalStatementImpl(node);
      }
      else if (type == GANTT_TODAY_MARKER_STATEMENT) {
        return new MermaidGanttTodayMarkerStatementImpl(node);
      }
      else if (type == GANTT_TOP_AXIS_STATEMENT) {
        return new MermaidGanttTopAxisStatementImpl(node);
      }
      else if (type == GANTT_WEEKDAY_STATEMENT) {
        return new MermaidGanttWeekdayStatementImpl(node);
      }
      else if (type == GENERIC) {
        return new MermaidGenericImpl(node);
      }
      else if (type == GENERIC_TYPE_ID) {
        return new MermaidGenericTypeIdImpl(node);
      }
      else if (type == GIT_GRAPH_BODY) {
        return new MermaidGitGraphBodyImpl(node);
      }
      else if (type == GIT_GRAPH_BRANCH_IDENTIFIER) {
        return new MermaidGitGraphBranchIdentifierImpl(node);
      }
      else if (type == GIT_GRAPH_HEADER) {
        return new MermaidGitGraphHeaderImpl(node);
      }
      else if (type == ICON_STATEMENT) {
        return new MermaidIconStatementImpl(node);
      }
      else if (type == IDENTIFIER) {
        return new MermaidIdentifierImpl(node);
      }
      else if (type == IDENTIFYING_COMPLEX_SANKEY_TEXT) {
        return new MermaidIdentifyingComplexSankeyTextImpl(node);
      }
      else if (type == IDENTIFYING_QUOTED_SANKEY_FIELD) {
        return new MermaidIdentifyingQuotedSankeyFieldImpl(node);
      }
      else if (type == IDENTIFYING_QUOTED_SANKEY_FIELD_VALUE) {
        return new MermaidIdentifyingQuotedSankeyFieldValueImpl(node);
      }
      else if (type == ID_ALIAS) {
        return new MermaidIdAliasImpl(node);
      }
      else if (type == JOURNEY_BODY) {
        return new MermaidJourneyBodyImpl(node);
      }
      else if (type == JOURNEY_DATA_STATEMENT) {
        return new MermaidJourneyDataStatementImpl(node);
      }
      else if (type == JOURNEY_HEADER) {
        return new MermaidJourneyHeaderImpl(node);
      }
      else if (type == JOURNEY_NAMED_DATA) {
        return new MermaidJourneyNamedDataImpl(node);
      }
      else if (type == JOURNEY_SECTION_BLOCK) {
        return new MermaidJourneySectionBlockImpl(node);
      }
      else if (type == JOURNEY_SECTION_HEADER) {
        return new MermaidJourneySectionHeaderImpl(node);
      }
      else if (type == JOURNEY_SECTION_STATEMENT) {
        return new MermaidJourneySectionStatementImpl(node);
      }
      else if (type == LEFT_ID) {
        return new MermaidLeftIdImpl(node);
      }
      else if (type == LINE_STATEMENT) {
        return new MermaidLineStatementImpl(node);
      }
      else if (type == LINE_TYPE) {
        return new MermaidLineTypeImpl(node);
      }
      else if (type == LINKS_STATEMENT) {
        return new MermaidLinksStatementImpl(node);
      }
      else if (type == LINKS_VALUES) {
        return new MermaidLinksValuesImpl(node);
      }
      else if (type == LINK_STATEMENT) {
        return new MermaidLinkStatementImpl(node);
      }
      else if (type == LINK_STYLE_STATEMENT) {
        return new MermaidLinkStyleStatementImpl(node);
      }
      else if (type == LOOP_HEADER) {
        return new MermaidLoopHeaderImpl(node);
      }
      else if (type == LOOP_STATEMENT) {
        return new MermaidLoopStatementImpl(node);
      }
      else if (type == MARKDOWN_VALUE) {
        return new MermaidMarkdownValueImpl(node);
      }
      else if (type == MEMBER_ATTRIBUTE) {
        return new MermaidMemberAttributeImpl(node);
      }
      else if (type == MEMBER_STATEMENT) {
        return new MermaidMemberStatementImpl(node);
      }
      else if (type == MERGE_STATEMENT) {
        return new MermaidMergeStatementImpl(node);
      }
      else if (type == MINDMAP_BODY) {
        return new MermaidMindmapBodyImpl(node);
      }
      else if (type == MINDMAP_CLASS_STATEMENT) {
        return new MermaidMindmapClassStatementImpl(node);
      }
      else if (type == MINDMAP_HEADER) {
        return new MermaidMindmapHeaderImpl(node);
      }
      else if (type == MINDMAP_NODE_DESCR) {
        return new MermaidMindmapNodeDescrImpl(node);
      }
      else if (type == MINDMAP_NODE_ID) {
        return new MermaidMindmapNodeIdImpl(node);
      }
      else if (type == MINDMAP_NODE_STATEMENT) {
        return new MermaidMindmapNodeStatementImpl(node);
      }
      else if (type == NAMESPACE_BLOCK) {
        return new MermaidNamespaceBlockImpl(node);
      }
      else if (type == NAMESPACE_HEADER) {
        return new MermaidNamespaceHeaderImpl(node);
      }
      else if (type == NAMESPACE_IDENTIFIER) {
        return new MermaidNamespaceIdentifierImpl(node);
      }
      else if (type == NAMESPACE_STATEMENT) {
        return new MermaidNamespaceStatementImpl(node);
      }
      else if (type == NODE_DESCRIPTION) {
        return new MermaidNodeDescriptionImpl(node);
      }
      else if (type == NODE_STATEMENT) {
        return new MermaidNodeStatementImpl(node);
      }
      else if (type == NODE_TEXT) {
        return new MermaidNodeTextImpl(node);
      }
      else if (type == NOTE_HEADER) {
        return new MermaidNoteHeaderImpl(node);
      }
      else if (type == NOTE_STATEMENT) {
        return new MermaidNoteStatementImpl(node);
      }
      else if (type == OPTION_HEADER) {
        return new MermaidOptionHeaderImpl(node);
      }
      else if (type == OPT_HEADER) {
        return new MermaidOptHeaderImpl(node);
      }
      else if (type == OPT_STATEMENT) {
        return new MermaidOptStatementImpl(node);
      }
      else if (type == PARENT_COMMIT_ID_ATTRIBUTE) {
        return new MermaidParentCommitIdAttributeImpl(node);
      }
      else if (type == PAR_HEADER) {
        return new MermaidParHeaderImpl(node);
      }
      else if (type == PAR_OVER_HEADER) {
        return new MermaidParOverHeaderImpl(node);
      }
      else if (type == PAR_OVER_STATEMENT) {
        return new MermaidParOverStatementImpl(node);
      }
      else if (type == PAR_STATEMENT) {
        return new MermaidParStatementImpl(node);
      }
      else if (type == PIE_BODY) {
        return new MermaidPieBodyImpl(node);
      }
      else if (type == PIE_DATA_STATEMENT) {
        return new MermaidPieDataStatementImpl(node);
      }
      else if (type == PIE_HEADER) {
        return new MermaidPieHeaderImpl(node);
      }
      else if (type == PLOT_DATA) {
        return new MermaidPlotDataImpl(node);
      }
      else if (type == POINT) {
        return new MermaidPointImpl(node);
      }
      else if (type == POINT_STATEMENT) {
        return new MermaidPointStatementImpl(node);
      }
      else if (type == QUADRANT_BODY) {
        return new MermaidQuadrantBodyImpl(node);
      }
      else if (type == QUADRANT_COMPLEX_TEXT) {
        return new MermaidQuadrantComplexTextImpl(node);
      }
      else if (type == QUADRANT_DETAILS_STATEMENT) {
        return new MermaidQuadrantDetailsStatementImpl(node);
      }
      else if (type == QUADRANT_HEADER) {
        return new MermaidQuadrantHeaderImpl(node);
      }
      else if (type == QUOTED_BRANCH_IDENTIFIER) {
        return new MermaidQuotedBranchIdentifierImpl(node);
      }
      else if (type == QUOTED_CLASS_IDENTIFIER) {
        return new MermaidQuotedClassIdentifierImpl(node);
      }
      else if (type == QUOTED_SANKEY_FIELD) {
        return new MermaidQuotedSankeyFieldImpl(node);
      }
      else if (type == QUOTED_SANKEY_FIELD_INNER_VALUE) {
        return new MermaidQuotedSankeyFieldInnerValueImpl(node);
      }
      else if (type == QUOTED_SANKEY_FIELD_VALUE) {
        return new MermaidQuotedSankeyFieldValueImpl(node);
      }
      else if (type == RECT_HEADER) {
        return new MermaidRectHeaderImpl(node);
      }
      else if (type == RECT_STATEMENT) {
        return new MermaidRectStatementImpl(node);
      }
      else if (type == RELATION) {
        return new MermaidRelationImpl(node);
      }
      else if (type == RELATIONSHIP) {
        return new MermaidRelationshipImpl(node);
      }
      else if (type == RELATIONSHIP_DEF) {
        return new MermaidRelationshipDefImpl(node);
      }
      else if (type == RELATION_STATEMENT) {
        return new MermaidRelationStatementImpl(node);
      }
      else if (type == RELATION_TYPE_LEFT) {
        return new MermaidRelationTypeLeftImpl(node);
      }
      else if (type == RELATION_TYPE_RIGHT) {
        return new MermaidRelationTypeRightImpl(node);
      }
      else if (type == REQUIREMENT_BLOCK) {
        return new MermaidRequirementBlockImpl(node);
      }
      else if (type == REQUIREMENT_DEF) {
        return new MermaidRequirementDefImpl(node);
      }
      else if (type == REQUIREMENT_DIAGRAM_BODY) {
        return new MermaidRequirementDiagramBodyImpl(node);
      }
      else if (type == REQUIREMENT_DIAGRAM_HEADER) {
        return new MermaidRequirementDiagramHeaderImpl(node);
      }
      else if (type == REQUIREMENT_HEADER) {
        return new MermaidRequirementHeaderImpl(node);
      }
      else if (type == REQUIREMENT_ID_ATTRIBUTE) {
        return new MermaidRequirementIdAttributeImpl(node);
      }
      else if (type == REQUIREMENT_RISK_ATTRIBUTE) {
        return new MermaidRequirementRiskAttributeImpl(node);
      }
      else if (type == REQUIREMENT_TEXT_ATTRIBUTE) {
        return new MermaidRequirementTextAttributeImpl(node);
      }
      else if (type == REQUIREMENT_TYPE) {
        return new MermaidRequirementTypeImpl(node);
      }
      else if (type == REQUIREMENT_VERIFY_METHOD_ATTRIBUTE) {
        return new MermaidRequirementVerifyMethodAttributeImpl(node);
      }
      else if (type == REQ_RELATIONSHIP) {
        return new MermaidReqRelationshipImpl(node);
      }
      else if (type == RIGHT_ID) {
        return new MermaidRightIdImpl(node);
      }
      else if (type == RISK_LEVEL) {
        return new MermaidRiskLevelImpl(node);
      }
      else if (type == SANKEY_BODY) {
        return new MermaidSankeyBodyImpl(node);
      }
      else if (type == SANKEY_HEADER) {
        return new MermaidSankeyHeaderImpl(node);
      }
      else if (type == SANKEY_RECORD_STATEMENT) {
        return new MermaidSankeyRecordStatementImpl(node);
      }
      else if (type == SECTION_TASK_DATA) {
        return new MermaidSectionTaskDataImpl(node);
      }
      else if (type == SEQUENCE_BODY) {
        return new MermaidSequenceBodyImpl(node);
      }
      else if (type == SEQUENCE_HEADER) {
        return new MermaidSequenceHeaderImpl(node);
      }
      else if (type == SIGNAL) {
        return new MermaidSignalImpl(node);
      }
      else if (type == SIGNAL_STATEMENT) {
        return new MermaidSignalStatementImpl(node);
      }
      else if (type == SIGNAL_TYPE) {
        return new MermaidSignalTypeImpl(node);
      }
      else if (type == SIMPLE_NOTE_CONTENT) {
        return new MermaidSimpleNoteContentImpl(node);
      }
      else if (type == SPACE_STATEMENT) {
        return new MermaidSpaceStatementImpl(node);
      }
      else if (type == SPACE_STATEMENT_INNER) {
        return new MermaidSpaceStatementInnerImpl(node);
      }
      else if (type == SPECIAL_STATE) {
        return new MermaidSpecialStateImpl(node);
      }
      else if (type == STATE_BLOCK) {
        return new MermaidStateBlockImpl(node);
      }
      else if (type == STATE_BODY) {
        return new MermaidStateBodyImpl(node);
      }
      else if (type == STATE_CLASS_DEF_STATEMENT) {
        return new MermaidStateClassDefStatementImpl(node);
      }
      else if (type == STATE_DECLARATION) {
        return new MermaidStateDeclarationImpl(node);
      }
      else if (type == STATE_DECLARATION_HEADER) {
        return new MermaidStateDeclarationHeaderImpl(node);
      }
      else if (type == STATE_HEADER) {
        return new MermaidStateHeaderImpl(node);
      }
      else if (type == STATE_ID) {
        return new MermaidStateIdImpl(node);
      }
      else if (type == STATE_NOTE) {
        return new MermaidStateNoteImpl(node);
      }
      else if (type == STATE_RELATION_STATEMENT) {
        return new MermaidStateRelationStatementImpl(node);
      }
      else if (type == STRING) {
        return new MermaidStringImpl(node);
      }
      else if (type == STYLED_VERTEX) {
        return new MermaidStyledVertexImpl(node);
      }
      else if (type == STYLE_OPTIONS) {
        return new MermaidStyleOptionsImpl(node);
      }
      else if (type == STYLE_STATEMENT) {
        return new MermaidStyleStatementImpl(node);
      }
      else if (type == STYLE_STATEMENT_TARGET) {
        return new MermaidStyleStatementTargetImpl(node);
      }
      else if (type == SUBGRAPH_BLOCK) {
        return new MermaidSubgraphBlockImpl(node);
      }
      else if (type == SUBGRAPH_HEADER) {
        return new MermaidSubgraphHeaderImpl(node);
      }
      else if (type == SUBGRAPH_NAME) {
        return new MermaidSubgraphNameImpl(node);
      }
      else if (type == SUBGRAPH_STATEMENT) {
        return new MermaidSubgraphStatementImpl(node);
      }
      else if (type == TIMELINE_BODY) {
        return new MermaidTimelineBodyImpl(node);
      }
      else if (type == TIMELINE_DATA_STATEMENT) {
        return new MermaidTimelineDataStatementImpl(node);
      }
      else if (type == TIMELINE_HEADER) {
        return new MermaidTimelineHeaderImpl(node);
      }
      else if (type == TIMELINE_SECTION_BLOCK) {
        return new MermaidTimelineSectionBlockImpl(node);
      }
      else if (type == TIMELINE_SECTION_HEADER) {
        return new MermaidTimelineSectionHeaderImpl(node);
      }
      else if (type == TIMELINE_SECTION_STATEMENT) {
        return new MermaidTimelineSectionStatementImpl(node);
      }
      else if (type == TITLE_STATEMENT) {
        return new MermaidTitleStatementImpl(node);
      }
      else if (type == VERIFY_TYPE) {
        return new MermaidVerifyTypeImpl(node);
      }
      else if (type == VERTEX) {
        return new MermaidVertexImpl(node);
      }
      else if (type == VERTEX_STATEMENT) {
        return new MermaidVertexStatementImpl(node);
      }
      else if (type == VERTEX_TEXT) {
        return new MermaidVertexTextImpl(node);
      }
      else if (type == XY_CHART_BODY) {
        return new MermaidXyChartBodyImpl(node);
      }
      else if (type == XY_CHART_HEADER) {
        return new MermaidXyChartHeaderImpl(node);
      }
      else if (type == X_AXIS_STATEMENT) {
        return new MermaidXAxisStatementImpl(node);
      }
      else if (type == Y_AXIS_STATEMENT) {
        return new MermaidYAxisStatementImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
