// This is a generated file. Not intended for manual editing.
package com.intellij.sh;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.sh.psi.ShCompositeElementType;
import com.intellij.sh.lexer.ShLazyBlockElementType;
import com.intellij.sh.lexer.ShLazyDoBlockElementType;
import com.intellij.sh.psi.ShTokenType;
import com.intellij.sh.psi.impl.*;

public interface ShTypes {

  IElementType ADD_EXPRESSION = new ShCompositeElementType("ADD_EXPRESSION");
  IElementType ARITHMETIC_CONDITION = new ShCompositeElementType("ARITHMETIC_CONDITION");
  IElementType ARITHMETIC_EXPANSION = new ShCompositeElementType("ARITHMETIC_EXPANSION");
  IElementType ARRAY_ASSIGNMENT = new ShCompositeElementType("ARRAY_ASSIGNMENT");
  IElementType ARRAY_EXPRESSION = new ShCompositeElementType("ARRAY_EXPRESSION");
  IElementType ASSIGNMENT_COMMAND = new ShCompositeElementType("ASSIGNMENT_COMMAND");
  IElementType ASSIGNMENT_EXPRESSION = new ShCompositeElementType("ASSIGNMENT_EXPRESSION");
  IElementType ASSIGNMENT_LIST = new ShCompositeElementType("ASSIGNMENT_LIST");
  IElementType BINARY_CONDITION = new ShCompositeElementType("BINARY_CONDITION");
  IElementType BITWISE_AND_EXPRESSION = new ShCompositeElementType("BITWISE_AND_EXPRESSION");
  IElementType BITWISE_EXCLUSIVE_OR_EXPRESSION = new ShCompositeElementType("BITWISE_EXCLUSIVE_OR_EXPRESSION");
  IElementType BITWISE_OR_EXPRESSION = new ShCompositeElementType("BITWISE_OR_EXPRESSION");
  IElementType BITWISE_SHIFT_EXPRESSION = new ShCompositeElementType("BITWISE_SHIFT_EXPRESSION");
  IElementType BLOCK = new ShLazyBlockElementType("BLOCK");
  IElementType BRACE_EXPANSION = new ShCompositeElementType("BRACE_EXPANSION");
  IElementType CASE_CLAUSE = new ShCompositeElementType("CASE_CLAUSE");
  IElementType CASE_COMMAND = new ShCompositeElementType("CASE_COMMAND");
  IElementType COMMAND = new ShCompositeElementType("COMMAND");
  IElementType COMMANDS_LIST = new ShCompositeElementType("COMMANDS_LIST");
  IElementType COMMAND_SUBSTITUTION_COMMAND = new ShCompositeElementType("COMMAND_SUBSTITUTION_COMMAND");
  IElementType COMMA_EXPRESSION = new ShCompositeElementType("COMMA_EXPRESSION");
  IElementType COMPARISON_CONDITION = new ShCompositeElementType("COMPARISON_CONDITION");
  IElementType COMPARISON_EXPRESSION = new ShCompositeElementType("COMPARISON_EXPRESSION");
  IElementType COMPOUND_LIST = new ShCompositeElementType("COMPOUND_LIST");
  IElementType CONDITION = new ShCompositeElementType("CONDITION");
  IElementType CONDITIONAL_COMMAND = new ShCompositeElementType("CONDITIONAL_COMMAND");
  IElementType CONDITIONAL_EXPRESSION = new ShCompositeElementType("CONDITIONAL_EXPRESSION");
  IElementType DO_BLOCK = new ShLazyDoBlockElementType("DO_BLOCK");
  IElementType ELIF_CLAUSE = new ShCompositeElementType("ELIF_CLAUSE");
  IElementType ELSE_CLAUSE = new ShCompositeElementType("ELSE_CLAUSE");
  IElementType EQUALITY_CONDITION = new ShCompositeElementType("EQUALITY_CONDITION");
  IElementType EQUALITY_EXPRESSION = new ShCompositeElementType("EQUALITY_EXPRESSION");
  IElementType EVAL_COMMAND = new ShCompositeElementType("EVAL_COMMAND");
  IElementType EXPRESSION = new ShCompositeElementType("EXPRESSION");
  IElementType EXP_EXPRESSION = new ShCompositeElementType("EXP_EXPRESSION");
  IElementType FOR_CLAUSE = new ShCompositeElementType("FOR_CLAUSE");
  IElementType FOR_COMMAND = new ShCompositeElementType("FOR_COMMAND");
  IElementType FUNCTION_DEFINITION = new ShCompositeElementType("FUNCTION_DEFINITION");
  IElementType GENERIC_COMMAND_DIRECTIVE = new ShCompositeElementType("GENERIC_COMMAND_DIRECTIVE");
  IElementType HEREDOC = new ShCompositeElementType("HEREDOC");
  IElementType IF_COMMAND = new ShCompositeElementType("IF_COMMAND");
  IElementType INCLUDE_COMMAND = new ShCompositeElementType("INCLUDE_COMMAND");
  IElementType INCLUDE_DIRECTIVE = new ShCompositeElementType("INCLUDE_DIRECTIVE");
  IElementType INDEX_EXPRESSION = new ShCompositeElementType("INDEX_EXPRESSION");
  IElementType LET_COMMAND = new ShCompositeElementType("LET_COMMAND");
  IElementType LIST_TERMINATOR = new ShCompositeElementType("LIST_TERMINATOR");
  IElementType LITERAL = new ShCompositeElementType("LITERAL");
  IElementType LITERAL_CONDITION = new ShCompositeElementType("LITERAL_CONDITION");
  IElementType LITERAL_EXPRESSION = new ShCompositeElementType("LITERAL_EXPRESSION");
  IElementType LOGICAL_AND_CONDITION = new ShCompositeElementType("LOGICAL_AND_CONDITION");
  IElementType LOGICAL_AND_EXPRESSION = new ShCompositeElementType("LOGICAL_AND_EXPRESSION");
  IElementType LOGICAL_BITWISE_NEGATION_EXPRESSION = new ShCompositeElementType("LOGICAL_BITWISE_NEGATION_EXPRESSION");
  IElementType LOGICAL_NEGATION_CONDITION = new ShCompositeElementType("LOGICAL_NEGATION_CONDITION");
  IElementType LOGICAL_OR_CONDITION = new ShCompositeElementType("LOGICAL_OR_CONDITION");
  IElementType LOGICAL_OR_EXPRESSION = new ShCompositeElementType("LOGICAL_OR_EXPRESSION");
  IElementType MUL_EXPRESSION = new ShCompositeElementType("MUL_EXPRESSION");
  IElementType NUMBER = new ShCompositeElementType("NUMBER");
  IElementType OLD_ARITHMETIC_EXPANSION = new ShCompositeElementType("OLD_ARITHMETIC_EXPANSION");
  IElementType PARENTHESES_CONDITION = new ShCompositeElementType("PARENTHESES_CONDITION");
  IElementType PARENTHESES_EXPRESSION = new ShCompositeElementType("PARENTHESES_EXPRESSION");
  IElementType PATTERN = new ShCompositeElementType("PATTERN");
  IElementType PIPELINE_COMMAND = new ShCompositeElementType("PIPELINE_COMMAND");
  IElementType POST_EXPRESSION = new ShCompositeElementType("POST_EXPRESSION");
  IElementType PRE_EXPRESSION = new ShCompositeElementType("PRE_EXPRESSION");
  IElementType PROCESS_SUBSTITUTION = new ShCompositeElementType("PROCESS_SUBSTITUTION");
  IElementType REDIRECTION = new ShCompositeElementType("REDIRECTION");
  IElementType REGEX_CONDITION = new ShCompositeElementType("REGEX_CONDITION");
  IElementType REGEX_PATTERN = new ShCompositeElementType("REGEX_PATTERN");
  IElementType SELECT_COMMAND = new ShCompositeElementType("SELECT_COMMAND");
  IElementType SHELL_COMMAND = new ShCompositeElementType("SHELL_COMMAND");
  IElementType SHELL_PARAMETER_EXPANSION = new ShCompositeElementType("SHELL_PARAMETER_EXPANSION");
  IElementType SIMPLE_COMMAND = new ShCompositeElementType("SIMPLE_COMMAND");
  IElementType SIMPLE_COMMAND_ELEMENT = new ShCompositeElementType("SIMPLE_COMMAND_ELEMENT");
  IElementType STRING = new ShCompositeElementType("STRING");
  IElementType SUBSHELL_COMMAND = new ShCompositeElementType("SUBSHELL_COMMAND");
  IElementType TEST_COMMAND = new ShCompositeElementType("TEST_COMMAND");
  IElementType THEN_CLAUSE = new ShCompositeElementType("THEN_CLAUSE");
  IElementType UNARY_CONDITION = new ShCompositeElementType("UNARY_CONDITION");
  IElementType UNARY_EXPRESSION = new ShCompositeElementType("UNARY_EXPRESSION");
  IElementType UNTIL_COMMAND = new ShCompositeElementType("UNTIL_COMMAND");
  IElementType VARIABLE = new ShCompositeElementType("VARIABLE");
  IElementType WHILE_COMMAND = new ShCompositeElementType("WHILE_COMMAND");

  IElementType AMP = new ShTokenType("&");
  IElementType AND_AND = new ShTokenType("&&");
  IElementType ARITH_SQUARE_LEFT = new ShTokenType("ARITH_SQUARE_LEFT");
  IElementType ARITH_SQUARE_RIGHT = new ShTokenType("ARITH_SQUARE_RIGHT");
  IElementType ASSIGN = new ShTokenType("=");
  IElementType BACKSLASH = new ShTokenType("\\\\");
  IElementType BANG = new ShTokenType("!");
  IElementType BITWISE_NEGATION = new ShTokenType("~");
  IElementType BIT_AND_ASSIGN = new ShTokenType("&=");
  IElementType BIT_OR_ASSIGN = new ShTokenType("|=");
  IElementType BIT_XOR_ASSIGN = new ShTokenType("^=");
  IElementType CASE = new ShTokenType("case");
  IElementType CASE_END = new ShTokenType(";;");
  IElementType CLOSE_BACKQUOTE = new ShTokenType("CLOSE_BACKQUOTE");
  IElementType CLOSE_QUOTE = new ShTokenType("CLOSE_QUOTE");
  IElementType COLON = new ShTokenType(":");
  IElementType COMMA = new ShTokenType(",");
  IElementType DIV = new ShTokenType("/");
  IElementType DIV_ASSIGN = new ShTokenType("/=");
  IElementType DO = new ShTokenType("do");
  IElementType DOLLAR = new ShTokenType("$");
  IElementType DONE = new ShTokenType("done");
  IElementType ELIF = new ShTokenType("elif");
  IElementType ELSE = new ShTokenType("else");
  IElementType EQ = new ShTokenType("==");
  IElementType ESAC = new ShTokenType("esac");
  IElementType EVAL = new ShTokenType("eval");
  IElementType EVAL_CONTENT = new ShTokenType("EVAL_CONTENT");
  IElementType EXPONENT = new ShTokenType("**");
  IElementType EXPR_CONDITIONAL_LEFT = new ShTokenType("[ ");
  IElementType EXPR_CONDITIONAL_RIGHT = new ShTokenType(" ]");
  IElementType FI = new ShTokenType("fi");
  IElementType FILEDESCRIPTOR = new ShTokenType("file descriptor");
  IElementType FOR = new ShTokenType("for");
  IElementType FUNCTION = new ShTokenType("function");
  IElementType GE = new ShTokenType(">=");
  IElementType GT = new ShTokenType(">");
  IElementType HEREDOC_CONTENT = new ShTokenType("HEREDOC_CONTENT");
  IElementType HEREDOC_MARKER_END = new ShTokenType("HEREDOC_MARKER_END");
  IElementType HEREDOC_MARKER_START = new ShTokenType("HEREDOC_MARKER_START");
  IElementType HEREDOC_MARKER_TAG = new ShTokenType("HEREDOC_MARKER_TAG");
  IElementType HEX = new ShTokenType("hex");
  IElementType IF = new ShTokenType("if");
  IElementType IN = new ShTokenType("in");
  IElementType INPUT_PROCESS_SUBSTITUTION = new ShTokenType("<(");
  IElementType INT = new ShTokenType("int");
  IElementType LE = new ShTokenType("<=");
  IElementType LEFT_CURLY = new ShTokenType("{");
  IElementType LEFT_DOUBLE_BRACKET = new ShTokenType("[[");
  IElementType LEFT_DOUBLE_PAREN = new ShTokenType("((");
  IElementType LEFT_PAREN = new ShTokenType("(");
  IElementType LEFT_SQUARE = new ShTokenType("[");
  IElementType LET = new ShTokenType("let");
  IElementType LINEFEED = new ShTokenType("\\n");
  IElementType LT = new ShTokenType("<");
  IElementType MINUS = new ShTokenType("-");
  IElementType MINUS_ASSIGN = new ShTokenType("-=");
  IElementType MINUS_MINUS = new ShTokenType("--");
  IElementType MOD = new ShTokenType("%");
  IElementType MOD_ASSIGN = new ShTokenType("%=");
  IElementType MULT = new ShTokenType("*");
  IElementType MULT_ASSIGN = new ShTokenType("*=");
  IElementType NE = new ShTokenType("!=");
  IElementType OCTAL = new ShTokenType("octal");
  IElementType OPEN_BACKQUOTE = new ShTokenType("OPEN_BACKQUOTE");
  IElementType OPEN_QUOTE = new ShTokenType("OPEN_QUOTE");
  IElementType OR_OR = new ShTokenType("||");
  IElementType OUTPUT_PROCESS_SUBSTITUTION = new ShTokenType(">(");
  IElementType PARAM_SEPARATOR = new ShTokenType("param_separator");
  IElementType PIPE = new ShTokenType("|");
  IElementType PIPE_AMP = new ShTokenType("|&");
  IElementType PLUS = new ShTokenType("+");
  IElementType PLUS_ASSIGN = new ShTokenType("+=");
  IElementType PLUS_PLUS = new ShTokenType("++");
  IElementType QMARK = new ShTokenType("?");
  IElementType RAW_STRING = new ShTokenType("RAW_STRING");
  IElementType REDIRECT_AMP_GREATER = new ShTokenType("&>");
  IElementType REDIRECT_AMP_GREATER_GREATER = new ShTokenType("&>>");
  IElementType REDIRECT_GREATER_AMP = new ShTokenType(">&");
  IElementType REDIRECT_GREATER_BAR = new ShTokenType(">|");
  IElementType REDIRECT_HERE_STRING = new ShTokenType("<<<");
  IElementType REDIRECT_LESS_AMP = new ShTokenType("<&");
  IElementType REDIRECT_LESS_GREATER = new ShTokenType("<>");
  IElementType REGEXP = new ShTokenType("=~");
  IElementType RIGHT_CURLY = new ShTokenType("}");
  IElementType RIGHT_DOUBLE_BRACKET = new ShTokenType("]]");
  IElementType RIGHT_DOUBLE_PAREN = new ShTokenType("))");
  IElementType RIGHT_PAREN = new ShTokenType(")");
  IElementType RIGHT_SQUARE = new ShTokenType("]");
  IElementType SELECT = new ShTokenType("select");
  IElementType SEMI = new ShTokenType(";");
  IElementType SHEBANG = new ShTokenType("shebang");
  IElementType SHIFT_LEFT = new ShTokenType("<<");
  IElementType SHIFT_LEFT_ASSIGN = new ShTokenType("<<=");
  IElementType SHIFT_RIGHT = new ShTokenType(">>");
  IElementType SHIFT_RIGHT_ASSIGN = new ShTokenType(">>=");
  IElementType STRING_CONTENT = new ShTokenType("STRING_CONTENT");
  IElementType TEST = new ShTokenType("test");
  IElementType THEN = new ShTokenType("then");
  IElementType UNTIL = new ShTokenType("until");
  IElementType VAR = new ShTokenType("var");
  IElementType WHILE = new ShTokenType("while");
  IElementType WORD = new ShTokenType("word");
  IElementType XOR = new ShTokenType("^");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == ADD_EXPRESSION) {
        return new ShAddExpressionImpl(node);
      }
      else if (type == ARITHMETIC_CONDITION) {
        return new ShArithmeticConditionImpl(node);
      }
      else if (type == ARITHMETIC_EXPANSION) {
        return new ShArithmeticExpansionImpl(node);
      }
      else if (type == ARRAY_ASSIGNMENT) {
        return new ShArrayAssignmentImpl(node);
      }
      else if (type == ARRAY_EXPRESSION) {
        return new ShArrayExpressionImpl(node);
      }
      else if (type == ASSIGNMENT_COMMAND) {
        return new ShAssignmentCommandImpl(node);
      }
      else if (type == ASSIGNMENT_EXPRESSION) {
        return new ShAssignmentExpressionImpl(node);
      }
      else if (type == ASSIGNMENT_LIST) {
        return new ShAssignmentListImpl(node);
      }
      else if (type == BINARY_CONDITION) {
        return new ShBinaryConditionImpl(node);
      }
      else if (type == BITWISE_AND_EXPRESSION) {
        return new ShBitwiseAndExpressionImpl(node);
      }
      else if (type == BITWISE_EXCLUSIVE_OR_EXPRESSION) {
        return new ShBitwiseExclusiveOrExpressionImpl(node);
      }
      else if (type == BITWISE_OR_EXPRESSION) {
        return new ShBitwiseOrExpressionImpl(node);
      }
      else if (type == BITWISE_SHIFT_EXPRESSION) {
        return new ShBitwiseShiftExpressionImpl(node);
      }
      else if (type == BLOCK) {
        return new ShBlockImpl(node);
      }
      else if (type == BRACE_EXPANSION) {
        return new ShBraceExpansionImpl(node);
      }
      else if (type == CASE_CLAUSE) {
        return new ShCaseClauseImpl(node);
      }
      else if (type == CASE_COMMAND) {
        return new ShCaseCommandImpl(node);
      }
      else if (type == COMMAND) {
        return new ShCommandImpl(node);
      }
      else if (type == COMMANDS_LIST) {
        return new ShCommandsListImpl(node);
      }
      else if (type == COMMAND_SUBSTITUTION_COMMAND) {
        return new ShCommandSubstitutionCommandImpl(node);
      }
      else if (type == COMMA_EXPRESSION) {
        return new ShCommaExpressionImpl(node);
      }
      else if (type == COMPARISON_CONDITION) {
        return new ShComparisonConditionImpl(node);
      }
      else if (type == COMPARISON_EXPRESSION) {
        return new ShComparisonExpressionImpl(node);
      }
      else if (type == COMPOUND_LIST) {
        return new ShCompoundListImpl(node);
      }
      else if (type == CONDITIONAL_COMMAND) {
        return new ShConditionalCommandImpl(node);
      }
      else if (type == CONDITIONAL_EXPRESSION) {
        return new ShConditionalExpressionImpl(node);
      }
      else if (type == DO_BLOCK) {
        return new ShDoBlockImpl(node);
      }
      else if (type == ELIF_CLAUSE) {
        return new ShElifClauseImpl(node);
      }
      else if (type == ELSE_CLAUSE) {
        return new ShElseClauseImpl(node);
      }
      else if (type == EQUALITY_CONDITION) {
        return new ShEqualityConditionImpl(node);
      }
      else if (type == EQUALITY_EXPRESSION) {
        return new ShEqualityExpressionImpl(node);
      }
      else if (type == EVAL_COMMAND) {
        return new ShEvalCommandImpl(node);
      }
      else if (type == EXP_EXPRESSION) {
        return new ShExpExpressionImpl(node);
      }
      else if (type == FOR_CLAUSE) {
        return new ShForClauseImpl(node);
      }
      else if (type == FOR_COMMAND) {
        return new ShForCommandImpl(node);
      }
      else if (type == FUNCTION_DEFINITION) {
        return new ShFunctionDefinitionImpl(node);
      }
      else if (type == GENERIC_COMMAND_DIRECTIVE) {
        return new ShGenericCommandDirectiveImpl(node);
      }
      else if (type == HEREDOC) {
        return new ShHeredocImpl(node);
      }
      else if (type == IF_COMMAND) {
        return new ShIfCommandImpl(node);
      }
      else if (type == INCLUDE_COMMAND) {
        return new ShIncludeCommandImpl(node);
      }
      else if (type == INCLUDE_DIRECTIVE) {
        return new ShIncludeDirectiveImpl(node);
      }
      else if (type == INDEX_EXPRESSION) {
        return new ShIndexExpressionImpl(node);
      }
      else if (type == LET_COMMAND) {
        return new ShLetCommandImpl(node);
      }
      else if (type == LIST_TERMINATOR) {
        return new ShListTerminatorImpl(node);
      }
      else if (type == LITERAL) {
        return new ShLiteralImpl(node);
      }
      else if (type == LITERAL_CONDITION) {
        return new ShLiteralConditionImpl(node);
      }
      else if (type == LITERAL_EXPRESSION) {
        return new ShLiteralExpressionImpl(node);
      }
      else if (type == LOGICAL_AND_CONDITION) {
        return new ShLogicalAndConditionImpl(node);
      }
      else if (type == LOGICAL_AND_EXPRESSION) {
        return new ShLogicalAndExpressionImpl(node);
      }
      else if (type == LOGICAL_BITWISE_NEGATION_EXPRESSION) {
        return new ShLogicalBitwiseNegationExpressionImpl(node);
      }
      else if (type == LOGICAL_NEGATION_CONDITION) {
        return new ShLogicalNegationConditionImpl(node);
      }
      else if (type == LOGICAL_OR_CONDITION) {
        return new ShLogicalOrConditionImpl(node);
      }
      else if (type == LOGICAL_OR_EXPRESSION) {
        return new ShLogicalOrExpressionImpl(node);
      }
      else if (type == MUL_EXPRESSION) {
        return new ShMulExpressionImpl(node);
      }
      else if (type == NUMBER) {
        return new ShNumberImpl(node);
      }
      else if (type == OLD_ARITHMETIC_EXPANSION) {
        return new ShOldArithmeticExpansionImpl(node);
      }
      else if (type == PARENTHESES_CONDITION) {
        return new ShParenthesesConditionImpl(node);
      }
      else if (type == PARENTHESES_EXPRESSION) {
        return new ShParenthesesExpressionImpl(node);
      }
      else if (type == PATTERN) {
        return new ShPatternImpl(node);
      }
      else if (type == PIPELINE_COMMAND) {
        return new ShPipelineCommandImpl(node);
      }
      else if (type == POST_EXPRESSION) {
        return new ShPostExpressionImpl(node);
      }
      else if (type == PRE_EXPRESSION) {
        return new ShPreExpressionImpl(node);
      }
      else if (type == PROCESS_SUBSTITUTION) {
        return new ShProcessSubstitutionImpl(node);
      }
      else if (type == REDIRECTION) {
        return new ShRedirectionImpl(node);
      }
      else if (type == REGEX_CONDITION) {
        return new ShRegexConditionImpl(node);
      }
      else if (type == REGEX_PATTERN) {
        return new ShRegexPatternImpl(node);
      }
      else if (type == SELECT_COMMAND) {
        return new ShSelectCommandImpl(node);
      }
      else if (type == SHELL_COMMAND) {
        return new ShShellCommandImpl(node);
      }
      else if (type == SHELL_PARAMETER_EXPANSION) {
        return new ShShellParameterExpansionImpl(node);
      }
      else if (type == SIMPLE_COMMAND) {
        return new ShSimpleCommandImpl(node);
      }
      else if (type == SIMPLE_COMMAND_ELEMENT) {
        return new ShSimpleCommandElementImpl(node);
      }
      else if (type == STRING) {
        return new ShStringImpl(node);
      }
      else if (type == SUBSHELL_COMMAND) {
        return new ShSubshellCommandImpl(node);
      }
      else if (type == TEST_COMMAND) {
        return new ShTestCommandImpl(node);
      }
      else if (type == THEN_CLAUSE) {
        return new ShThenClauseImpl(node);
      }
      else if (type == UNARY_CONDITION) {
        return new ShUnaryConditionImpl(node);
      }
      else if (type == UNARY_EXPRESSION) {
        return new ShUnaryExpressionImpl(node);
      }
      else if (type == UNTIL_COMMAND) {
        return new ShUntilCommandImpl(node);
      }
      else if (type == VARIABLE) {
        return new ShVariableImpl(node);
      }
      else if (type == WHILE_COMMAND) {
        return new ShWhileCommandImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
