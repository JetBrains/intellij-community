package com.github.firsttimeinforever.mermaid.lang.lexer

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

object MermaidTokens {
  @JvmField
  val OPEN_DIRECTIVE = MermaidToken("OPEN_DIRECTIVE")

  @JvmField
  val CLOSE_DIRECTIVE = MermaidToken("CLOSE_DIRECTIVE")

//  @JvmField
//  val TYPE_DIRECTIVE = MermaidToken("TYPE_DIRECTIVE")
//
//  @JvmField
//  val PROPERTY_KEY = MermaidToken("PROPERTY_KEY")
//
//  @JvmField
//  val PROPERTY_VALUE = MermaidToken("PROPERTY_VALUE")
//
//  @JvmField
//  val ARG_DIRECTIVE = MermaidToken("ARG_DIRECTIVE")

  @JvmField
  val DIRECTIVE_TEXT = MermaidToken("DIRECTIVE_TEXT")

  @JvmField
  val COLON = MermaidToken("COLON")

  @JvmField
  val COMMA = MermaidToken("COMMA")

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
  val SEMICOLON = MermaidToken("SEMICOLON")

  @JvmField
  val DOUBLE_QUOTE = MermaidToken("DOUBLE_QUOTE")

  @JvmField
  val STRING_VALUE = MermaidToken("STRING_VALUE")

  @JvmField
  val LINE_COMMENT = MermaidToken("LINE_COMMENT")

  @JvmField
  val COMMENT_TEXT = MermaidToken("COMMENT_TEXT")

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
    val START_ARROW = MermaidToken("Flowchart.START_ARROW")

    @JvmField
    val LINK_TEXT = MermaidToken("Flowchart.LINK_TEXT")

    @JvmField
    val SEP = MermaidToken("Flowchart.SEP")

    @JvmField
    val AMPERSAND = MermaidToken("AMPERSAND")

    @JvmField
    val SUBGRAPH = MermaidToken("SUBGRAPH")

    @JvmField
    val LINK_STYLE = MermaidToken("LINK_STYLE")

    @JvmField
    val STYLE = MermaidToken("STYLE")

    @JvmField
    val STYLE_TARGET = MermaidToken("STYLE_TARGET")

    @JvmField
    val STYLE_OPT = MermaidToken("STYLE_OPT")

    @JvmField
    val STYLE_VAL = MermaidToken("STYLE_VAL")

    @JvmField
    val CLASS_DEF = MermaidToken("CLASS_DEF")

    @JvmField
    val DEFAULT = MermaidToken("DEFAULT")
  }

  object Sequence {
    @JvmField
    val SEQUENCE = MermaidToken("Sequence.SEQUENCE")

    @JvmField
    val PARTICIPANT = MermaidToken("Sequence.PARTICIPANT")

    @JvmField
    val ACTOR = MermaidToken("Sequence.ACTOR")

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
    val AND = MermaidToken("Sequence.AND")

    @JvmField
    val RECT = MermaidToken("Sequence.RECT")

    @JvmField
    val AUTONUMBER = MermaidToken("Sequence.AUTONUMBER")

    @JvmField
    val LINK = MermaidToken("Sequence.LINK")

    @JvmField
    val LINKS = MermaidToken("Sequence.LINKS")
  }

  object ClassDiagram {
    @JvmField
    val CLASS_DIAGRAM = MermaidToken("CLASS_DIAGRAM")

    @JvmField
    val GENERIC_TYPE = MermaidToken("GENERIC_TYPE")

    @JvmField
    val EXTENSION_START = MermaidToken("EXTENSION_START")

    @JvmField
    val EXTENSION_END = MermaidToken("EXTENSION_END")

    @JvmField
    val DEPENDENCY_START = MermaidToken("DEPENDENCY_START")

    @JvmField
    val DEPENDENCY_END = MermaidToken("DEPENDENCY_END")

    @JvmField
    val COMPOSITION = MermaidToken("COMPOSITION")

    @JvmField
    val AGGREGATION = MermaidToken("AGGREGATION")

    @JvmField
    val LINE = MermaidToken("LINE")

    @JvmField
    val DOTTED_LINE = MermaidToken("DOTTED_LINE")
  }

  object StateDiagram {
    @JvmField
    val STATE_DIAGRAM = MermaidToken("STATE_DIAGRAM")

    @JvmField
    val STATE = MermaidToken("STATE")
  }

  object EntityRelationship {
    @JvmField
    val ENTITY_RELATIONSHIP = MermaidToken("ENTITY_RELATIONSHIP")

    @JvmField
    val ZERO_OR_ONE_LEFT = MermaidToken("ZERO_OR_ONE_LEFT")

    @JvmField
    val ONE_OR_MORE_LEFT = MermaidToken("ONE_OR_MORE_LEFT")

    @JvmField
    val ONLY_ONE = MermaidToken("ONLY_ONE")

    @JvmField
    val ZERO_OR_MORE_LEFT = MermaidToken("ZERO_OR_MORE_LEFT")

    @JvmField
    val ZERO_OR_ONE_RIGHT = MermaidToken("ZERO_OR_ONE_RIGHT")

    @JvmField
    val ONE_OR_MORE_RIGHT = MermaidToken("ONE_OR_MORE_RIGHT")

    @JvmField
    val ZERO_OR_MORE_RIGHT = MermaidToken("ZERO_OR_MORE_RIGHT")

    @JvmField
    val IDENTIFYING = MermaidToken("IDENTIFYING")

    @JvmField
    val NON_IDENTIFYING = MermaidToken("NON_IDENTIFYING")

    @JvmField
    val ATTR_KEY = MermaidToken("ATTR_KEY")

  }

  object Gantt {
    @JvmField
    val GANTT = MermaidToken("GANTT")

    @JvmField
    val DATE_FORMAT = MermaidToken("DATE_FORMAT")

    @JvmField
    val EXCLUDES = MermaidToken("EXCLUDES")

    @JvmField
    val INCLUDES = MermaidToken("INCLUDES")

    @JvmField
    val AXIS_FORMAT = MermaidToken("AXIS_FORMAT")
  }

  object Requirement {
    @JvmField
    val REQUIREMENT_DIAGRAM = MermaidToken("REQUIREMENT_DIAGRAM")

    @JvmField
    val REQUIREMENT = MermaidToken("REQUIREMENT")

    @JvmField
    val FUNCTIONAL_REQUIREMENT = MermaidToken("FUNCTIONAL_REQUIREMENT")

    @JvmField
    val INTERFACE_REQUIREMENT = MermaidToken("INTERFACE_REQUIREMENT")

    @JvmField
    val PERFORMANCE_REQUIREMENT = MermaidToken("PERFORMANCE_REQUIREMENT")

    @JvmField
    val PHYSICAL_REQUIREMENT = MermaidToken("PHYSICAL_REQUIREMENT")

    @JvmField
    val DESIGN_CONSTRAINT = MermaidToken("DESIGN_CONSTRAINT")

    @JvmField
    val ELEMENT = MermaidToken("ELEMENT")

    @JvmField
    val LOW = MermaidToken("LOW")

    @JvmField
    val MEDIUM = MermaidToken("MEDIUM")

    @JvmField
    val HIGH = MermaidToken("HIGH")

    @JvmField
    val ANALYSIS = MermaidToken("ANALYSIS")

    @JvmField
    val INSPECTION = MermaidToken("INSPECTION")

    @JvmField
    val TEST = MermaidToken("TEST")

    @JvmField
    val DEMONSTRATION = MermaidToken("DEMONSTRATION")

    @JvmField
    val ID_KEYWORD = MermaidToken("ID_KEYWORD")

    @JvmField
    val TEXT = MermaidToken("TEXT")

    @JvmField
    val RISK = MermaidToken("RISK")

    @JvmField
    val VERIFY_METHOD = MermaidToken("VERIFY_METHOD")

    @JvmField
    val TYPE = MermaidToken("TYPE")

    @JvmField
    val DOCREF = MermaidToken("DOCREF")

    @JvmField
    val CONTAINS = MermaidToken("CONTAINS")

    @JvmField
    val COPIES = MermaidToken("COPIES")

    @JvmField
    val DERIVES = MermaidToken("DERIVES")

    @JvmField
    val SATISFIES = MermaidToken("SATISFIES")

    @JvmField
    val VERIFIES = MermaidToken("VERIFIES")

    @JvmField
    val REFINES = MermaidToken("REFINES")

    @JvmField
    val TRACES = MermaidToken("TRACES")

    @JvmField
    val ARROW_LEFT = MermaidToken("ARROW_LEFT")

    @JvmField
    val ARROW_RIGHT = MermaidToken("ARROW_RIGHT")

    @JvmField
    val REQ_LINE = MermaidToken("REQ_LINE")
  }
}
