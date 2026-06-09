// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.lang.lexer

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

object MermaidTokens {
  //region General
  @JvmField
  val ACC_TITLE = MermaidToken("ACC_TITLE")

  @JvmField
  val ACC_DESCR = MermaidToken("ACC_DESCR")

  @JvmField
  val ACC_TITLE_VALUE = MermaidToken("ACC_TITLE_VALUE")

  @JvmField
  val ACC_DESCR_VALUE = MermaidToken("ACC_DESCR_VALUE")

  @JvmField
  val ACC_DESCR_MULTILINE_VALUE = MermaidToken("ACC_DESCR_MULTILINE_VALUE")

  @JvmField
  val COLON = MermaidToken("COLON")

  @JvmField
  val COMMA = MermaidToken("COMMA")

  @JvmField
  val DOT = MermaidToken("DOT")

  @JvmField
  val OPEN_CURLY = MermaidToken("OPEN_CURLY")

  @JvmField
  val CLOSE_CURLY = MermaidToken("CLOSE_CURLY")

  @JvmField
  val OPEN_ROUND = MermaidToken("OPEN_ROUND")

  @JvmField
  val CLOSE_ROUND = MermaidToken("CLOSE_ROUND")

  @JvmField
  val OPEN_SQUARE = MermaidToken("OPEN_SQUARE")

  @JvmField
  val CLOSE_SQUARE = MermaidToken("CLOSE_SQUARE")

  @JvmField
  val OPEN_ANGLE = MermaidToken("OPEN_ANGLE")

  @JvmField
  val CLOSE_ANGLE = MermaidToken("CLOSE_ANGLE")

  @JvmField
  val SEMICOLON = MermaidToken("SEMICOLON")

  @JvmField
  val DOUBLE_QUOTE = MermaidToken("DOUBLE_QUOTE")

  @JvmField
  val BACK_QUOTE = MermaidToken("BACK_QUOTE")

  @JvmField
  val STRING_VALUE = MermaidToken("STRING_VALUE")

  @JvmField
  val MD_STRING_VALUE = MermaidToken("MD_STRING_VALUE")

  @JvmField
  val LINE_COMMENT = MermaidToken("LINE_COMMENT")

  @JvmField
  val WHITE_SPACE: IElementType = TokenType.WHITE_SPACE

  @JvmField
  val BAD_CHARACTER: IElementType = TokenType.BAD_CHARACTER

  @JvmField
  val EOL = MermaidToken("EOL")

  @JvmField
  val TITLE = MermaidToken("TITLE")

  @JvmField
  val TITLE_VALUE = MermaidToken("TITLE_VALUE")

  @JvmField
  val IGNORED = MermaidToken("IGNORED")

  @JvmField
  val ID = MermaidToken("ID")

  @JvmField
  val NUM = MermaidToken("NUM")

  @JvmField
  val ALIAS = MermaidToken("ALIAS")

  @JvmField
  val PLUS = MermaidToken("PLUS")

  @JvmField
  val MINUS = MermaidToken("MINUS")

  @JvmField
  val END = MermaidToken("END")

  @JvmField
  val CLASS = MermaidToken("CLASS")

  @JvmField
  val TILDA = MermaidToken("TILDA")

  @JvmField
  val DIRECTION = MermaidToken("DIRECTION")

  @JvmField
  val DIR = MermaidToken("DIR")

  @JvmField
  val POUND = MermaidToken("POUND")

  @JvmField
  val STAR = MermaidToken("STAR")

  @JvmField
  val DOLLAR = MermaidToken("DOLLAR")

  @JvmField
  val STYLE_SEPARATOR = MermaidToken("STYLE_SEPARATOR")

  @JvmField
  val LABEL = MermaidToken("LABEL")

  @JvmField
  val AS = MermaidToken("AS")

  @JvmField
  val NOTE = MermaidToken("NOTE")

  @JvmField
  val NOTE_CONTENT = MermaidToken("NOTE_CONTENT")

  @JvmField
  val RIGHT_OF = MermaidToken("RIGHT_OF")

  @JvmField
  val LEFT_OF = MermaidToken("LEFT_OF")

  @JvmField
  val ARROW = MermaidToken("ARROW")

  @JvmField
  val START_ARROW = MermaidToken("START_ARROW")

  @JvmField
  val ANNOTATION_START = MermaidToken("ANNOTATION_START")

  @JvmField
  val ANNOTATION_END = MermaidToken("ANNOTATION_END")

  @JvmField
  val ANNOTATION_VALUE = MermaidToken("ANNOTATION_VALUE")

  @JvmField
  val SECTION = MermaidToken("SECTION")

  @JvmField
  val SECTION_TITLE = MermaidToken("SECTION_TITLE")

  @JvmField
  val TASK_NAME = MermaidToken("TASK_NAME")

  @JvmField
  val TASK_DATA = MermaidToken("TASK_DATA")

  @JvmField
  val CLICK = MermaidToken("CLICK")

  @JvmField
  val CALLBACK = MermaidToken("CALLBACK")

  @JvmField
  val LINK = MermaidToken("LINK")

  @JvmField
  val HREF = MermaidToken("HREF")

  @JvmField
  val CALL = MermaidToken("CALL")

  @JvmField
  val CLICK_DATA = MermaidToken("CLICK_DATA")

  @JvmField
  val LINK_TARGET = MermaidToken("LINK_TARGET")

  @JvmField
  val TYPE = MermaidToken("TYPE")

  @JvmField
  val ID_KEYWORD = MermaidToken("ID_KEYWORD")

  @JvmField
  val ATTRIBUTE_WORD = MermaidToken("ATTRIBUTE_WORD")

  @JvmField
  val DEFAULT = MermaidToken("DEFAULT")

  @JvmField
  val CLASS_DEF = MermaidToken("CLASS_DEF")

  @JvmField
  val STYLE_OPT = MermaidToken("STYLE_OPT")

  @JvmField
  val STYLE_VAL = MermaidToken("STYLE_VAL")

  @JvmField
  val GENERIC_TYPE = MermaidToken("GENERIC_TYPE")

  @JvmField
  val X_AXIS = MermaidToken("X_AXIS")

  @JvmField
  val Y_AXIS = MermaidToken("Y_AXIS")

  @JvmField
  val STYLE = MermaidToken("STYLE")

  @JvmField
  val STYLE_TARGET = MermaidToken("STYLE_TARGET")

  @JvmField
  val NODE_DESCR_START = MermaidToken("NODE_DESCR_START")

  @JvmField
  val NODE_DESCR_END = MermaidToken("NODE_DESCR_END")
  //endregion

  object Directives {
    @JvmField
    val OPEN_DIRECTIVE = MermaidToken("Directives.OPEN_DIRECTIVE")

    @JvmField
    val CLOSE_DIRECTIVE = MermaidToken("Directives.CLOSE_DIRECTIVE")

    @JvmField
    val DIRECTIVE_TEXT = MermaidToken("Directives.DIRECTIVE_TEXT")
  }

  object Frontmatter {
    @JvmField
    val FRONTMATTER_START = MermaidToken("FRONTMATTER_START")

    @JvmField
    val FRONTMATTER_VALUE = MermaidToken("FRONTMATTER_VALUE")

    @JvmField
    val FRONTMATTER_END = MermaidToken("FRONTMATTER_END")
  }

  object Pie {
    @JvmField
    val PIE = MermaidToken("Pie.PIE")

    @JvmField
    val SHOW_DATA = MermaidToken("Pie.SHOW_DATA")

    @JvmField
    val VALUE = MermaidToken("Pie.VALUE")
  }

  object Journey {
    @JvmField
    val JOURNEY = MermaidToken("Journey.JOURNEY")
  }

  object Flowchart {
    @JvmField
    val FLOWCHART = MermaidToken("Flowchart.FLOWCHART")

    @JvmField
    val STADIUM_START = MermaidToken("Flowchart.STADIUM_START")

    @JvmField
    val STADIUM_END = MermaidToken("Flowchart.STADIUM_END")

    @JvmField
    val SUBROUTINE_START = MermaidToken("Flowchart.SUBROUTINE_START")

    @JvmField
    val SUBROUTINE_END = MermaidToken("Flowchart.SUBROUTINE_END")

    @JvmField
    val CYLINDER_START = MermaidToken("Flowchart.CYLINDER_START")

    @JvmField
    val CYLINDER_END = MermaidToken("Flowchart.CYLINDER_END")

    @JvmField
    val CIRCLE_START = MermaidToken("Flowchart.CIRCLE_START")

    @JvmField
    val CIRCLE_END = MermaidToken("Flowchart.CIRCLE_END")

    @JvmField
    val ASYMMETRIC_START = MermaidToken("Flowchart.ASYMMETRIC_START")

    @JvmField
    val DIAMOND_START = MermaidToken("Flowchart.DIAMOND_START")

    @JvmField
    val DIAMOND_END = MermaidToken("Flowchart.DIAMOND_END")

    @JvmField
    val HEXAGON_START = MermaidToken("Flowchart.HEXAGON_START")

    @JvmField
    val HEXAGON_END = MermaidToken("Flowchart.HEXAGON_END")

    @JvmField
    val TRAP_START = MermaidToken("Flowchart.TRAP_START")

    @JvmField
    val TRAP_END = MermaidToken("Flowchart.TRAP_END")

    @JvmField
    val INV_TRAP_START = MermaidToken("Flowchart.INV_TRAP_START")

    @JvmField
    val INV_TRAP_END = MermaidToken("Flowchart.INV_TRAP_END")

    @JvmField
    val DOUBLE_CIRCLE_START = MermaidToken("Flowchart.DOUBLE_CIRCLE_START")

    @JvmField
    val DOUBLE_CIRCLE_END = MermaidToken("Flowchart.DOUBLE_CIRCLE_END")

    @JvmField
    val LINK_TEXT = MermaidToken("Flowchart.LINK_TEXT")

    @JvmField
    val SEP = MermaidToken("Flowchart.SEP")

    @JvmField
    val AMPERSAND = MermaidToken("Flowchart.AMPERSAND")

    @JvmField
    val SUBGRAPH = MermaidToken("Flowchart.SUBGRAPH")

    @JvmField
    val LINK_STYLE = MermaidToken("Flowchart.LINK_STYLE")

    @JvmField
    val CLASS_ID_STYLE = MermaidToken("Flowchart.CLASS_ID_STYLE")
  }

  object Sequence {
    @JvmField
    val SEQUENCE = MermaidToken("Sequence.SEQUENCE")

    @JvmField
    val PARTICIPANT = MermaidToken("Sequence.PARTICIPANT")

    @JvmField
    val ACTOR = MermaidToken("Sequence.ACTOR")

    @JvmField
    val CREATE = MermaidToken("Sequence.CREATE")

    @JvmField
    val DESTROY = MermaidToken("Sequence.DESTROY")

    @JvmField
    val SOLID_ARROW = MermaidToken("Sequence.SOLID_ARROW")

    @JvmField
    val DOTTED_ARROW = MermaidToken("Sequence.DOTTED_ARROW")

    @JvmField
    val SOLID_OPEN_ARROW = MermaidToken("Sequence.SOLID_OPEN_ARROW")

    @JvmField
    val DOTTED_OPEN_ARROW = MermaidToken("Sequence.DOTTED_OPEN_ARROW")

    @JvmField
    val SOLID_CROSS = MermaidToken("Sequence.SOLID_CROSS")

    @JvmField
    val DOTTED_CROSS = MermaidToken("Sequence.DOTTED_CROSS")

    @JvmField
    val SOLID_POINT = MermaidToken("Sequence.SOLID_POINT")

    @JvmField
    val DOTTED_POINT = MermaidToken("Sequence.DOTTED_POINT")

    @JvmField
    val MESSAGE = MermaidToken("Sequence.MESSAGE")

    @JvmField
    val ACTIVATE = MermaidToken("Sequence.ACTIVATE")

    @JvmField
    val DEACTIVATE = MermaidToken("Sequence.DEACTIVATE")

    @JvmField
    val OVER = MermaidToken("Sequence.OVER")

    @JvmField
    val LOOP = MermaidToken("Sequence.LOOP")

    @JvmField
    val ALT = MermaidToken("Sequence.ALT")

    @JvmField
    val ELSE = MermaidToken("Sequence.ELSE")

    @JvmField
    val OPT = MermaidToken("Sequence.OPT")

    @JvmField
    val PAR = MermaidToken("Sequence.PAR")

    @JvmField
    val PAR_OVER = MermaidToken("Sequence.PAR_OVER")

    @JvmField
    val AND = MermaidToken("Sequence.AND")

    @JvmField
    val RECT = MermaidToken("Sequence.RECT")

    @JvmField
    val CRITICAL = MermaidToken("Sequence.CRITICAL")

    @JvmField
    val OPTION = MermaidToken("Sequence.OPTION")

    @JvmField
    val BREAK = MermaidToken("Sequence.BREAK")

    @JvmField
    val AUTONUMBER = MermaidToken("Sequence.AUTONUMBER")

    @JvmField
    val OFF = MermaidToken("Sequence.OFF")

    @JvmField
    val LINKS = MermaidToken("Sequence.LINKS")

    @JvmField
    val BOX = MermaidToken("Sequence.BOX")

    @JvmField
    val CONTROL_ID = MermaidToken("Sequence.CONTROL_ID")
  }

  object ClassDiagram {
    @JvmField
    val CLASS_DIAGRAM = MermaidToken("ClassDiagram.CLASS_DIAGRAM")

    @JvmField
    val CLASS_ID = MermaidToken("ClassDiagram.CLASS_ID")

    @JvmField
    val EXTENSION_START = MermaidToken("ClassDiagram.EXTENSION_START")

    @JvmField
    val EXTENSION_END = MermaidToken("ClassDiagram.EXTENSION_END")

    @JvmField
    val DEPENDENCY_START = MermaidToken("ClassDiagram.DEPENDENCY_START")

    @JvmField
    val DEPENDENCY_END = MermaidToken("ClassDiagram.DEPENDENCY_END")

    @JvmField
    val COMPOSITION = MermaidToken("ClassDiagram.COMPOSITION")

    @JvmField
    val AGGREGATION = MermaidToken("ClassDiagram.AGGREGATION")

    @JvmField
    val LOLLIPOP = MermaidToken("ClassDiagram.LOLLIPOP")

    @JvmField
    val LINE = MermaidToken("ClassDiagram.LINE")

    @JvmField
    val DOTTED_LINE = MermaidToken("ClassDiagram.DOTTED_LINE")

    @JvmField
    val NOTE_FOR = MermaidToken("ClassDiagram.NOTE_FOR")

    @JvmField
    val NAMESPACE = MermaidToken("ClassDiagram.NAMESPACE")
  }

  object StateDiagram {
    @JvmField
    val STATE_DIAGRAM = MermaidToken("StateDiagram.STATE_DIAGRAM")

    @JvmField
    val STATE = MermaidToken("StateDiagram.STATE")

    @JvmField
    val DIVIDER = MermaidToken("StateDiagram.DIVIDER")

    @JvmField
    val CLASS_DEF_ID = MermaidToken("StateDiagram.CLASS_DEF_ID")

    @JvmField
    val CLASS_DEF_STYLE_OPT = MermaidToken("StateDiagram.CLASS_DEF_STYLE_OPT")

    @JvmField
    val CLASS_ENTITY_IDS = MermaidToken("StateDiagram.CLASS_ENTITY_IDS")

    @JvmField
    val STYLE_CLASS = MermaidToken("StateDiagram.STYLE_CLASS")

    @JvmField
    val SCALE = MermaidToken("StateDiagram.SCALE")

    @JvmField
    val WIDTH = MermaidToken("StateDiagram.WIDTH")

    @JvmField
    val WIDTH_VALUE = MermaidToken("StateDiagram.WIDTH_VALUE")
  }

  object EntityRelationship {
    @JvmField
    val ENTITY_RELATIONSHIP = MermaidToken("EntityRelationship.ENTITY_RELATIONSHIP")

    @JvmField
    val ZERO_OR_ONE = MermaidToken("EntityRelationship.ZERO_OR_ONE")

    @JvmField
    val ONE_OR_MORE = MermaidToken("EntityRelationship.ONE_OR_MORE")

    @JvmField
    val ZERO_OR_MORE = MermaidToken("EntityRelationship.ZERO_OR_MORE")

    @JvmField
    val ONLY_ONE = MermaidToken("EntityRelationship.ONLY_ONE")

    @JvmField
    val MD_PARENT = MermaidToken("EntityRelationship.MD_PARENT")

    @JvmField
    val IDENTIFYING = MermaidToken("EntityRelationship.IDENTIFYING")

    @JvmField
    val NON_IDENTIFYING = MermaidToken("EntityRelationship.NON_IDENTIFYING")

    @JvmField
    val ATTR_KEY = MermaidToken("EntityRelationship.ATTR_KEY")

  }

  object Gantt {
    @JvmField
    val GANTT = MermaidToken("Gantt.GANTT")

    @JvmField
    val DATE_FORMAT = MermaidToken("Gantt.DATE_FORMAT")

    @JvmField
    val INCLUSIVE_END_DATES = MermaidToken("Gantt.INCLUSIVE_END_DATES")

    @JvmField
    val TOP_AXIS = MermaidToken("Gantt.TOP_AXIS")

    @JvmField
    val EXCLUDES = MermaidToken("Gantt.EXCLUDES")

    @JvmField
    val INCLUDES = MermaidToken("Gantt.INCLUDES")

    @JvmField
    val AXIS_FORMAT = MermaidToken("Gantt.AXIS_FORMAT")

    @JvmField
    val TODAY_MARKER = MermaidToken("Gantt.TODAY_MARKER")

    @JvmField
    val TICK_INTERVAL = MermaidToken("Gantt.TICK_INTERVAL")

    @JvmField
    val GANTT_VALUE = MermaidToken("Gantt.GANTT_VALUE")

    @JvmField
    val WEEKDAY = MermaidToken("Gantt.WEEKDAY")

    @JvmField
    val MONDAY = MermaidToken("Gantt.MONDAY")

    @JvmField
    val TUESDAY = MermaidToken("Gantt.TUESDAY")

    @JvmField
    val WEDNESDAY = MermaidToken("Gantt.WEDNESDAY")

    @JvmField
    val THURSDAY = MermaidToken("Gantt.THURSDAY")

    @JvmField
    val FRIDAY = MermaidToken("Gantt.FRIDAY")

    @JvmField
    val SATURDAY = MermaidToken("Gantt.SATURDAY")

    @JvmField
    val SUNDAY = MermaidToken("Gantt.SUNDAY")
  }

  object Requirement {
    @JvmField
    val REQUIREMENT_DIAGRAM = MermaidToken("Requirement.REQUIREMENT_DIAGRAM")

    @JvmField
    val REQUIREMENT = MermaidToken("Requirement.REQUIREMENT")

    @JvmField
    val FUNCTIONAL_REQUIREMENT = MermaidToken("Requirement.FUNCTIONAL_REQUIREMENT")

    @JvmField
    val INTERFACE_REQUIREMENT = MermaidToken("Requirement.INTERFACE_REQUIREMENT")

    @JvmField
    val PERFORMANCE_REQUIREMENT = MermaidToken("Requirement.PERFORMANCE_REQUIREMENT")

    @JvmField
    val PHYSICAL_REQUIREMENT = MermaidToken("Requirement.PHYSICAL_REQUIREMENT")

    @JvmField
    val DESIGN_CONSTRAINT = MermaidToken("Requirement.DESIGN_CONSTRAINT")

    @JvmField
    val ELEMENT = MermaidToken("Requirement.ELEMENT")

    @JvmField
    val LOW = MermaidToken("Requirement.LOW")

    @JvmField
    val MEDIUM = MermaidToken("Requirement.MEDIUM")

    @JvmField
    val HIGH = MermaidToken("Requirement.HIGH")

    @JvmField
    val ANALYSIS = MermaidToken("Requirement.ANALYSIS")

    @JvmField
    val INSPECTION = MermaidToken("Requirement.INSPECTION")

    @JvmField
    val TEST = MermaidToken("Requirement.TEST")

    @JvmField
    val DEMONSTRATION = MermaidToken("Requirement.DEMONSTRATION")

    @JvmField
    val TEXT = MermaidToken("Requirement.TEXT")

    @JvmField
    val RISK = MermaidToken("Requirement.RISK")

    @JvmField
    val VERIFY_METHOD = MermaidToken("Requirement.VERIFY_METHOD")

    @JvmField
    val DOCREF = MermaidToken("Requirement.DOCREF")

    @JvmField
    val CONTAINS = MermaidToken("Requirement.CONTAINS")

    @JvmField
    val COPIES = MermaidToken("Requirement.COPIES")

    @JvmField
    val DERIVES = MermaidToken("Requirement.DERIVES")

    @JvmField
    val SATISFIES = MermaidToken("Requirement.SATISFIES")

    @JvmField
    val VERIFIES = MermaidToken("Requirement.VERIFIES")

    @JvmField
    val REFINES = MermaidToken("Requirement.REFINES")

    @JvmField
    val TRACES = MermaidToken("Requirement.TRACES")

    @JvmField
    val ARROW_LEFT = MermaidToken("Requirement.ARROW_LEFT")

    @JvmField
    val ARROW_RIGHT = MermaidToken("Requirement.ARROW_RIGHT")

    @JvmField
    val REQ_LINE = MermaidToken("Requirement.REQ_LINE")
  }

  object GitGraph {
    @JvmField
    val GIT_GRAPH = MermaidToken("GitGraph.GIT_GRAPH")

    @JvmField
    val COMMIT = MermaidToken("GitGraph.COMMIT")

    @JvmField
    val BRANCH = MermaidToken("GitGraph.BRANCH")

    @JvmField
    val CHECKOUT = MermaidToken("GitGraph.CHECKOUT")

    @JvmField
    val MERGE = MermaidToken("GitGraph.MERGE")

    @JvmField
    val TAG = MermaidToken("GitGraph.TAG")

    @JvmField
    val MSG = MermaidToken("GitGraph.MSG")

    @JvmField
    val PARENT = MermaidToken("GitGraph.PARENT")

    @JvmField
    val CHERRY_PICK = MermaidToken("GitGraph.CHERRY_PICK")

    @JvmField
    val ORDER = MermaidToken("GitGraph.ORDER")

    @JvmField
    val NORMAL = MermaidToken("GitGraph.NORMAL")

    @JvmField
    val REVERSE = MermaidToken("GitGraph.REVERSE")

    @JvmField
    val HIGHLIGHT = MermaidToken("GitGraph.HIGHLIGHT")
  }

  object C4 {
    @JvmField
    val C4_CONTEXT = MermaidToken("C4.C4_CONTEXT")

    @JvmField
    val C4_CONTAINER = MermaidToken("C4.C4_CONTAINER")

    @JvmField
    val C4_COMPONENT = MermaidToken("C4.C4_COMPONENT")

    @JvmField
    val C4_DYNAMIC = MermaidToken("C4.C4_DYNAMIC")

    @JvmField
    val C4_DEPLOYMENT = MermaidToken("C4.C4_DEPLOYMENT")

    @JvmField
    val PERSON_EXT = MermaidToken("C4.PERSON_EXT")

    @JvmField
    val PERSON = MermaidToken("C4.PERSON")

    @JvmField
    val SYSTEM_EXT_QUEUE = MermaidToken("C4.SYSTEM_EXT_QUEUE")

    @JvmField
    val SYSTEM_EXT_DB = MermaidToken("C4.SYSTEM_EXT_DB")

    @JvmField
    val SYSTEM_EXT = MermaidToken("C4.SYSTEM_EXT")

    @JvmField
    val SYSTEM_QUEUE = MermaidToken("C4.SYSTEM_QUEUE")

    @JvmField
    val SYSTEM_DB = MermaidToken("C4.SYSTEM_DB")

    @JvmField
    val SYSTEM = MermaidToken("C4.SYSTEM")

    @JvmField
    val BOUNDARY = MermaidToken("C4.BOUNDARY")

    @JvmField
    val ENTERPRISE_BOUNDARY = MermaidToken("C4.ENTERPRISE_BOUNDARY")

    @JvmField
    val SYSTEM_BOUNDARY = MermaidToken("C4.SYSTEM_BOUNDARY")

    @JvmField
    val CONTAINER_EXT_QUEUE = MermaidToken("C4.CONTAINER_EXT_QUEUE")

    @JvmField
    val CONTAINER_EXT_DB = MermaidToken("C4.CONTAINER_EXT_DB")

    @JvmField
    val CONTAINER_EXT = MermaidToken("C4.CONTAINER_EXT")

    @JvmField
    val CONTAINER_QUEUE = MermaidToken("C4.CONTAINER_QUEUE")

    @JvmField
    val CONTAINER_DB = MermaidToken("C4.CONTAINER_DB")

    @JvmField
    val CONTAINER = MermaidToken("C4.CONTAINER")

    @JvmField
    val CONTAINER_BOUNDARY = MermaidToken("C4.CONTAINER_BOUNDARY")

    @JvmField
    val COMPONENT_EXT_QUEUE = MermaidToken("C4.COMPONENT_EXT_QUEUE")

    @JvmField
    val COMPONENT_EXT_DB = MermaidToken("C4.COMPONENT_EXT_DB")

    @JvmField
    val COMPONENT_EXT = MermaidToken("C4.COMPONENT_EXT")

    @JvmField
    val COMPONENT_QUEUE = MermaidToken("C4.COMPONENT_QUEUE")

    @JvmField
    val COMPONENT_DB = MermaidToken("C4.COMPONENT_DB")

    @JvmField
    val COMPONENT = MermaidToken("C4.COMPONENT")

    @JvmField
    val NODE = MermaidToken("C4.NODE")

    @JvmField
    val NODE_L = MermaidToken("C4.NODE_L")

    @JvmField
    val NODE_R = MermaidToken("C4.NODE_R")

    @JvmField
    val REL = MermaidToken("C4.REL")

    @JvmField
    val BIREL = MermaidToken("C4.BIREL")

    @JvmField
    val REL_U = MermaidToken("C4.REL_U")

    @JvmField
    val REL_D = MermaidToken("C4.REL_D")

    @JvmField
    val REL_L = MermaidToken("C4.REL_L")

    @JvmField
    val REL_R = MermaidToken("C4.REL_R")

    @JvmField
    val REL_B = MermaidToken("C4.REL_B")

    @JvmField
    val REL_INDEX = MermaidToken("C4.REL_INDEX")

    @JvmField
    val UPDATE_EL_STYLE = MermaidToken("C4.UPDATE_EL_STYLE")

    @JvmField
    val UPDATE_REL_STYLE = MermaidToken("C4.UPDATE_REL_STYLE")

    @JvmField
    val UPDATE_LAYOUT_CONFIG = MermaidToken("C4.UPDATE_LAYOUT_CONFIG")

    @JvmField
    val C4_ATTRIBUTE = MermaidToken("C4.C4_ATTRIBUTE")

    @JvmField
    val EQUALITY = MermaidToken("C4.EQUALITY")
  }

  object Mindmap {
    @JvmField
    val MINDMAP = MermaidToken("Mindmap.MINDMAP")

    @JvmField
    val OPEN_ICON = MermaidToken("Mindmap.OPEN_ICON")

    @JvmField
    val CLOSE_ICON = MermaidToken("Mindmap.CLOSE_ICON")

    @JvmField
    val ICON_VALUE = MermaidToken("Mindmap.ICON_VALUE")

    @JvmField
    val NODE_DESCR = MermaidToken("Mindmap.NODE_DESCR")
  }

  object Timeline {
    @JvmField
    val TIMELINE = MermaidToken("Timeline.TIMELINE")
  }

  object Quadrant {
    @JvmField
    val QUADRANT_CHART = MermaidToken("Quadrant.QUADRANT_CHART")

    @JvmField
    val QUADRANT = MermaidToken("Quadrant.QUADRANT")

    @JvmField
    val QUADRANT_TEXT = MermaidToken("Quadrant.QUADRANT_TEXT")
  }


  object ZenUML {
    @JvmField
    val ZEN_UML = MermaidToken("ZenUML.ZEN_UML")
  }


  object Sankey {
    @JvmField
    val SANKEY = MermaidToken("Sankey.SANKEY")

    @JvmField
    val SANKEY_TEXT = MermaidToken("Sankey.SANKEY_TEXT")
  }


  object XYChart {
    @JvmField
    val XY_CHART = MermaidToken("XYChart.XY_CHART")

    @JvmField
    val ORIENTATION_VALUE = MermaidToken("XYChart.ORIENTATION_VALUE")

    @JvmField
    val LINE_KEYWORD = MermaidToken("XYChart.LINE_KEYWORD")

    @JvmField
    val BAR_KEYWORD = MermaidToken("XYChart.BAR_KEYWORD")

    @JvmField
    val XY_CHART_TEXT = MermaidToken("XYChart.XY_CHART_TEXT")
  }


  object Block {
    @JvmField
    val BLOCK_DIAGRAM = MermaidToken("Block.BLOCK_DIAGRAM")

    @JvmField
    val BLOCK = MermaidToken("Block.BLOCK")

    @JvmField
    val COLUMNS = MermaidToken("Block.COLUMNS")

    @JvmField
    val SPACE = MermaidToken("Block.SPACE")

    @JvmField
    val INTERPOLATE = MermaidToken("Block.INTERPOLATE")

    @JvmField
    val ARROW_DESCR_START = MermaidToken("Block.ARROW_DESCR_START")

    @JvmField
    val ARROW_DESCR_END = MermaidToken("Block.ARROW_DESCR_END")

    @JvmField
    val ARROW_DIR = MermaidToken("Block.ARROW_DIR")

    @JvmField
    val AUTO = MermaidToken("Block.AUTO")
  }
}
