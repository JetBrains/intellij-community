// This is a generated file. Not intended for manual editing.
package com.intellij.bash;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.bash.psi.BashCompositeElementType;
import com.intellij.bash.psi.BashTokenType;
import com.intellij.bash.psi.impl.*;

public interface BashTypes {

  IElementType ADD_EXPRESSION = new BashCompositeElementType("ADD_EXPRESSION");
  IElementType ARITHMETIC_EXPANSION = new BashCompositeElementType("ARITHMETIC_EXPANSION");
  IElementType ARRAY_ASSIGNMENT = new BashCompositeElementType("ARRAY_ASSIGNMENT");
  IElementType ARRAY_EXPRESSION = new BashCompositeElementType("ARRAY_EXPRESSION");
  IElementType ASSIGNMENT_COMMAND = new BashCompositeElementType("ASSIGNMENT_COMMAND");
  IElementType ASSIGNMENT_EXPRESSION = new BashCompositeElementType("ASSIGNMENT_EXPRESSION");
  IElementType ASSIGNMENT_LIST = new BashCompositeElementType("ASSIGNMENT_LIST");
  IElementType BASH_EXPANSION = new BashCompositeElementType("BASH_EXPANSION");
  IElementType BITWISE_AND_EXPRESSION = new BashCompositeElementType("BITWISE_AND_EXPRESSION");
  IElementType BITWISE_EXCLUSIVE_OR_EXPRESSION = new BashCompositeElementType("BITWISE_EXCLUSIVE_OR_EXPRESSION");
  IElementType BITWISE_OR_EXPRESSION = new BashCompositeElementType("BITWISE_OR_EXPRESSION");
  IElementType BITWISE_SHIFT_EXPRESSION = new BashCompositeElementType("BITWISE_SHIFT_EXPRESSION");
  IElementType BLOCK = new BashCompositeElementType("BLOCK");
  IElementType CASE_CLAUSE = new BashCompositeElementType("CASE_CLAUSE");
  IElementType CASE_COMMAND = new BashCompositeElementType("CASE_COMMAND");
  IElementType COMMAND = new BashCompositeElementType("COMMAND");
  IElementType COMMANDS_LIST = new BashCompositeElementType("COMMANDS_LIST");
  IElementType COMMAND_SUBSTITUTION_COMMAND = new BashCompositeElementType("COMMAND_SUBSTITUTION_COMMAND");
  IElementType COMMA_EXPRESSION = new BashCompositeElementType("COMMA_EXPRESSION");
  IElementType COMPARISON_EXPRESSION = new BashCompositeElementType("COMPARISON_EXPRESSION");
  IElementType COMPOUND_LIST = new BashCompositeElementType("COMPOUND_LIST");
  IElementType CONDITIONAL_COMMAND = new BashCompositeElementType("CONDITIONAL_COMMAND");
  IElementType CONDITIONAL_EXPRESSION = new BashCompositeElementType("CONDITIONAL_EXPRESSION");
  IElementType DO_BLOCK = new BashCompositeElementType("DO_BLOCK");
  IElementType ELIF_CLAUSE = new BashCompositeElementType("ELIF_CLAUSE");
  IElementType ELSE_CLAUSE = new BashCompositeElementType("ELSE_CLAUSE");
  IElementType EQUALITY_EXPRESSION = new BashCompositeElementType("EQUALITY_EXPRESSION");
  IElementType EXPRESSION = new BashCompositeElementType("EXPRESSION");
  IElementType EXP_EXPRESSION = new BashCompositeElementType("EXP_EXPRESSION");
  IElementType FOR_COMMAND = new BashCompositeElementType("FOR_COMMAND");
  IElementType FUNCTION_DEF = new BashCompositeElementType("FUNCTION_DEF");
  IElementType GENERIC_COMMAND_DIRECTIVE = new BashCompositeElementType("GENERIC_COMMAND_DIRECTIVE");
  IElementType HEREDOC = new BashCompositeElementType("HEREDOC");
  IElementType IF_COMMAND = new BashCompositeElementType("IF_COMMAND");
  IElementType INCLUDE_COMMAND = new BashCompositeElementType("INCLUDE_COMMAND");
  IElementType INCLUDE_DIRECTIVE = new BashCompositeElementType("INCLUDE_DIRECTIVE");
  IElementType INDEX_EXPRESSION = new BashCompositeElementType("INDEX_EXPRESSION");
  IElementType LET_COMMAND = new BashCompositeElementType("LET_COMMAND");
  IElementType LIST_TERMINATOR = new BashCompositeElementType("LIST_TERMINATOR");
  IElementType LITERAL_EXPRESSION = new BashCompositeElementType("LITERAL_EXPRESSION");
  IElementType LOGICAL_AND_EXPRESSION = new BashCompositeElementType("LOGICAL_AND_EXPRESSION");
  IElementType LOGICAL_BITWISE_NEGATION_EXPRESSION = new BashCompositeElementType("LOGICAL_BITWISE_NEGATION_EXPRESSION");
  IElementType LOGICAL_OR_EXPRESSION = new BashCompositeElementType("LOGICAL_OR_EXPRESSION");
  IElementType MUL_EXPRESSION = new BashCompositeElementType("MUL_EXPRESSION");
  IElementType OLD_ARITHMETIC_EXPANSION = new BashCompositeElementType("OLD_ARITHMETIC_EXPANSION");
  IElementType PARENTHESES_EXPRESSION = new BashCompositeElementType("PARENTHESES_EXPRESSION");
  IElementType PATTERN = new BashCompositeElementType("PATTERN");
  IElementType PIPELINE = new BashCompositeElementType("PIPELINE");
  IElementType PIPELINE_COMMAND = new BashCompositeElementType("PIPELINE_COMMAND");
  IElementType POST_EXPRESSION = new BashCompositeElementType("POST_EXPRESSION");
  IElementType PRE_EXPRESSION = new BashCompositeElementType("PRE_EXPRESSION");
  IElementType PROCESS_SUBSTITUTION = new BashCompositeElementType("PROCESS_SUBSTITUTION");
  IElementType REDIRECTION = new BashCompositeElementType("REDIRECTION");
  IElementType REDIRECTION_LIST = new BashCompositeElementType("REDIRECTION_LIST");
  IElementType SELECT_COMMAND = new BashCompositeElementType("SELECT_COMMAND");
  IElementType SHELL_COMMAND = new BashCompositeElementType("SHELL_COMMAND");
  IElementType SHELL_PARAMETER_EXPANSION = new BashCompositeElementType("SHELL_PARAMETER_EXPANSION");
  IElementType SIMPLE_COMMAND = new BashCompositeElementType("SIMPLE_COMMAND");
  IElementType SIMPLE_COMMAND_ELEMENT = new BashCompositeElementType("SIMPLE_COMMAND_ELEMENT");
  IElementType STRING = new BashCompositeElementType("STRING");
  IElementType SUBSHELL_COMMAND = new BashCompositeElementType("SUBSHELL_COMMAND");
  IElementType THEN_CLAUSE = new BashCompositeElementType("THEN_CLAUSE");
  IElementType TIMESPEC = new BashCompositeElementType("TIMESPEC");
  IElementType TIME_OPT = new BashCompositeElementType("TIME_OPT");
  IElementType TRAP_COMMAND = new BashCompositeElementType("TRAP_COMMAND");
  IElementType UNARY_EXPRESSION = new BashCompositeElementType("UNARY_EXPRESSION");
  IElementType UNTIL_COMMAND = new BashCompositeElementType("UNTIL_COMMAND");
  IElementType VARIABLE = new BashCompositeElementType("VARIABLE");
  IElementType WHILE_COMMAND = new BashCompositeElementType("WHILE_COMMAND");

  IElementType ADD_EQ = new BashTokenType("+=");
  IElementType AMP = new BashTokenType("&");
  IElementType AND_AND = new BashTokenType("&&");
  IElementType ARITH_MINUS = new BashTokenType("-");
  IElementType ARITH_MINUS_MINUS = new BashTokenType("--");
  IElementType ARITH_PLUS = new BashTokenType("+");
  IElementType ARITH_PLUS_PLUS = new BashTokenType("++");
  IElementType ARITH_SQUARE_LEFT = new BashTokenType("ARITH_SQUARE_LEFT");
  IElementType ARITH_SQUARE_RIGHT = new BashTokenType("ARITH_SQUARE_RIGHT");
  IElementType ASSIGNMENT_WORD = new BashTokenType("assignment_word");
  IElementType AT = new BashTokenType("@");
  IElementType BACKQUOTE = new BashTokenType("`");
  IElementType BACKSLASH = new BashTokenType("\\\\");
  IElementType BANG = new BashTokenType("!");
  IElementType CASE = new BashTokenType("case");
  IElementType CASE_END = new BashTokenType(";;");
  IElementType COLON = new BashTokenType(":");
  IElementType COMMA = new BashTokenType(",");
  IElementType DIV = new BashTokenType("/");
  IElementType DO = new BashTokenType("do");
  IElementType DOLLAR = new BashTokenType("$");
  IElementType DONE = new BashTokenType("done");
  IElementType ELIF = new BashTokenType("elif");
  IElementType ELSE = new BashTokenType("else");
  IElementType EQ = new BashTokenType("=");
  IElementType ESAC = new BashTokenType("esac");
  IElementType EXPONENT = new BashTokenType("**");
  IElementType EXPR_CONDITIONAL_LEFT = new BashTokenType("[ ");
  IElementType EXPR_CONDITIONAL_RIGHT = new BashTokenType(" ]");
  IElementType FI = new BashTokenType("fi");
  IElementType FILEDESCRIPTOR = new BashTokenType("file descriptor");
  IElementType FOR = new BashTokenType("for");
  IElementType FUNCTION = new BashTokenType("function");
  IElementType GE = new BashTokenType(">=");
  IElementType GT = new BashTokenType(">");
  IElementType HEREDOC_CONTENT = new BashTokenType("HEREDOC_CONTENT");
  IElementType HEREDOC_MARKER_END = new BashTokenType("HEREDOC_MARKER_END");
  IElementType HEREDOC_MARKER_IGNORING_TABS_END = new BashTokenType("HEREDOC_MARKER_IGNORING_TABS_END");
  IElementType HEREDOC_MARKER_START = new BashTokenType("HEREDOC_MARKER_START");
  IElementType HEREDOC_MARKER_TAG = new BashTokenType("HEREDOC_MARKER_TAG");
  IElementType HEX = new BashTokenType("hex");
  IElementType IF = new BashTokenType("if");
  IElementType INT = new BashTokenType("int");
  IElementType LE = new BashTokenType("<=");
  IElementType LEFT_CURLY = new BashTokenType("{");
  IElementType LEFT_DOUBLE_BRACKET = new BashTokenType("[[");
  IElementType LEFT_DOUBLE_PAREN = new BashTokenType("((");
  IElementType LEFT_PAREN = new BashTokenType("(");
  IElementType LEFT_SQUARE = new BashTokenType("[");
  IElementType LET = new BashTokenType("let");
  IElementType LINEFEED = new BashTokenType("\\n");
  IElementType LT = new BashTokenType("<");
  IElementType MOD = new BashTokenType("%");
  IElementType MULT = new BashTokenType("*");
  IElementType NUMBER = new BashTokenType("number");
  IElementType OCTAL = new BashTokenType("octal");
  IElementType OR_OR = new BashTokenType("||");
  IElementType PARAMETER_EXPANSION_BODY = new BashTokenType("parameter_expansion_body");
  IElementType PIPE = new BashTokenType("|");
  IElementType PIPE_AMP = new BashTokenType("|&");
  IElementType QUOTE = new BashTokenType("\"");
  IElementType RAW_STRING = new BashTokenType("RAW_STRING");
  IElementType REDIRECT_GREATER_AMP = new BashTokenType(">&");
  IElementType REDIRECT_GREATER_BAR = new BashTokenType(">|");
  IElementType REDIRECT_HERE_STRING = new BashTokenType("<<<");
  IElementType REDIRECT_LESS_AMP = new BashTokenType("<&");
  IElementType REDIRECT_LESS_GREATER = new BashTokenType("<>");
  IElementType RIGHT_CURLY = new BashTokenType("}");
  IElementType RIGHT_DOUBLE_BRACKET = new BashTokenType("]]");
  IElementType RIGHT_DOUBLE_PAREN = new BashTokenType("))");
  IElementType RIGHT_PAREN = new BashTokenType(")");
  IElementType RIGHT_SQUARE = new BashTokenType("]");
  IElementType SELECT = new BashTokenType("select");
  IElementType SEMI = new BashTokenType(";");
  IElementType SHEBANG = new BashTokenType("shebang");
  IElementType SHIFT_LEFT = new BashTokenType("<<");
  IElementType SHIFT_RIGHT = new BashTokenType(">>");
  IElementType STRING_BEGIN = new BashTokenType("string_begin");
  IElementType STRING_CONTENT = new BashTokenType("string_content");
  IElementType STRING_END = new BashTokenType("string_end");
  IElementType THEN = new BashTokenType("then");
  IElementType TIME = new BashTokenType("time");
  IElementType TRAP = new BashTokenType("trap");
  IElementType UNTIL = new BashTokenType("until");
  IElementType VAR = new BashTokenType("var");
  IElementType WHILE = new BashTokenType("while");
  IElementType WORD = new BashTokenType("word");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == ADD_EXPRESSION) {
        return new BashAddExpressionImpl(node);
      }
      else if (type == ARITHMETIC_EXPANSION) {
        return new BashArithmeticExpansionImpl(node);
      }
      else if (type == ARRAY_ASSIGNMENT) {
        return new BashArrayAssignmentImpl(node);
      }
      else if (type == ARRAY_EXPRESSION) {
        return new BashArrayExpressionImpl(node);
      }
      else if (type == ASSIGNMENT_COMMAND) {
        return new BashAssignmentCommandImpl(node);
      }
      else if (type == ASSIGNMENT_EXPRESSION) {
        return new BashAssignmentExpressionImpl(node);
      }
      else if (type == ASSIGNMENT_LIST) {
        return new BashAssignmentListImpl(node);
      }
      else if (type == BASH_EXPANSION) {
        return new BashBashExpansionImpl(node);
      }
      else if (type == BITWISE_AND_EXPRESSION) {
        return new BashBitwiseAndExpressionImpl(node);
      }
      else if (type == BITWISE_EXCLUSIVE_OR_EXPRESSION) {
        return new BashBitwiseExclusiveOrExpressionImpl(node);
      }
      else if (type == BITWISE_OR_EXPRESSION) {
        return new BashBitwiseOrExpressionImpl(node);
      }
      else if (type == BITWISE_SHIFT_EXPRESSION) {
        return new BashBitwiseShiftExpressionImpl(node);
      }
      else if (type == BLOCK) {
        return new BashBlockImpl(node);
      }
      else if (type == CASE_CLAUSE) {
        return new BashCaseClauseImpl(node);
      }
      else if (type == CASE_COMMAND) {
        return new BashCaseCommandImpl(node);
      }
      else if (type == COMMAND) {
        return new BashCommandImpl(node);
      }
      else if (type == COMMANDS_LIST) {
        return new BashCommandsListImpl(node);
      }
      else if (type == COMMAND_SUBSTITUTION_COMMAND) {
        return new BashCommandSubstitutionCommandImpl(node);
      }
      else if (type == COMMA_EXPRESSION) {
        return new BashCommaExpressionImpl(node);
      }
      else if (type == COMPARISON_EXPRESSION) {
        return new BashComparisonExpressionImpl(node);
      }
      else if (type == COMPOUND_LIST) {
        return new BashCompoundListImpl(node);
      }
      else if (type == CONDITIONAL_COMMAND) {
        return new BashConditionalCommandImpl(node);
      }
      else if (type == CONDITIONAL_EXPRESSION) {
        return new BashConditionalExpressionImpl(node);
      }
      else if (type == DO_BLOCK) {
        return new BashDoBlockImpl(node);
      }
      else if (type == ELIF_CLAUSE) {
        return new BashElifClauseImpl(node);
      }
      else if (type == ELSE_CLAUSE) {
        return new BashElseClauseImpl(node);
      }
      else if (type == EQUALITY_EXPRESSION) {
        return new BashEqualityExpressionImpl(node);
      }
      else if (type == EXP_EXPRESSION) {
        return new BashExpExpressionImpl(node);
      }
      else if (type == FOR_COMMAND) {
        return new BashForCommandImpl(node);
      }
      else if (type == FUNCTION_DEF) {
        return new BashFunctionDefImpl(node);
      }
      else if (type == GENERIC_COMMAND_DIRECTIVE) {
        return new BashGenericCommandDirectiveImpl(node);
      }
      else if (type == HEREDOC) {
        return new BashHeredocImpl(node);
      }
      else if (type == IF_COMMAND) {
        return new BashIfCommandImpl(node);
      }
      else if (type == INCLUDE_COMMAND) {
        return new BashIncludeCommandImpl(node);
      }
      else if (type == INCLUDE_DIRECTIVE) {
        return new BashIncludeDirectiveImpl(node);
      }
      else if (type == INDEX_EXPRESSION) {
        return new BashIndexExpressionImpl(node);
      }
      else if (type == LET_COMMAND) {
        return new BashLetCommandImpl(node);
      }
      else if (type == LIST_TERMINATOR) {
        return new BashListTerminatorImpl(node);
      }
      else if (type == LITERAL_EXPRESSION) {
        return new BashLiteralExpressionImpl(node);
      }
      else if (type == LOGICAL_AND_EXPRESSION) {
        return new BashLogicalAndExpressionImpl(node);
      }
      else if (type == LOGICAL_BITWISE_NEGATION_EXPRESSION) {
        return new BashLogicalBitwiseNegationExpressionImpl(node);
      }
      else if (type == LOGICAL_OR_EXPRESSION) {
        return new BashLogicalOrExpressionImpl(node);
      }
      else if (type == MUL_EXPRESSION) {
        return new BashMulExpressionImpl(node);
      }
      else if (type == OLD_ARITHMETIC_EXPANSION) {
        return new BashOldArithmeticExpansionImpl(node);
      }
      else if (type == PARENTHESES_EXPRESSION) {
        return new BashParenthesesExpressionImpl(node);
      }
      else if (type == PATTERN) {
        return new BashPatternImpl(node);
      }
      else if (type == PIPELINE) {
        return new BashPipelineImpl(node);
      }
      else if (type == PIPELINE_COMMAND) {
        return new BashPipelineCommandImpl(node);
      }
      else if (type == POST_EXPRESSION) {
        return new BashPostExpressionImpl(node);
      }
      else if (type == PRE_EXPRESSION) {
        return new BashPreExpressionImpl(node);
      }
      else if (type == PROCESS_SUBSTITUTION) {
        return new BashProcessSubstitutionImpl(node);
      }
      else if (type == REDIRECTION) {
        return new BashRedirectionImpl(node);
      }
      else if (type == REDIRECTION_LIST) {
        return new BashRedirectionListImpl(node);
      }
      else if (type == SELECT_COMMAND) {
        return new BashSelectCommandImpl(node);
      }
      else if (type == SHELL_COMMAND) {
        return new BashShellCommandImpl(node);
      }
      else if (type == SHELL_PARAMETER_EXPANSION) {
        return new BashShellParameterExpansionImpl(node);
      }
      else if (type == SIMPLE_COMMAND) {
        return new BashSimpleCommandImpl(node);
      }
      else if (type == SIMPLE_COMMAND_ELEMENT) {
        return new BashSimpleCommandElementImpl(node);
      }
      else if (type == STRING) {
        return new BashStringImpl(node);
      }
      else if (type == SUBSHELL_COMMAND) {
        return new BashSubshellCommandImpl(node);
      }
      else if (type == THEN_CLAUSE) {
        return new BashThenClauseImpl(node);
      }
      else if (type == TIMESPEC) {
        return new BashTimespecImpl(node);
      }
      else if (type == TIME_OPT) {
        return new BashTimeOptImpl(node);
      }
      else if (type == TRAP_COMMAND) {
        return new BashTrapCommandImpl(node);
      }
      else if (type == UNARY_EXPRESSION) {
        return new BashUnaryExpressionImpl(node);
      }
      else if (type == UNTIL_COMMAND) {
        return new BashUntilCommandImpl(node);
      }
      else if (type == VARIABLE) {
        return new BashVariableImpl(node);
      }
      else if (type == WHILE_COMMAND) {
        return new BashWhileCommandImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
