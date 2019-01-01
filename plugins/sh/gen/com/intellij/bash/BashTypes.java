// This is a generated file. Not intended for manual editing.
package com.intellij.bash;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.bash.psi.BashCompositeElementType;
import com.intellij.bash.psi.BashTokenType;
import com.intellij.bash.psi.impl.*;

public interface BashTypes {

  IElementType ASSIGNMENT_WORD_RULE = new BashCompositeElementType("ASSIGNMENT_WORD_RULE");
  IElementType BLOCK = new BashCompositeElementType("BLOCK");
  IElementType CASE_CLAUSE = new BashCompositeElementType("CASE_CLAUSE");
  IElementType CASE_COMMAND = new BashCompositeElementType("CASE_COMMAND");
  IElementType COMMAND = new BashCompositeElementType("COMMAND");
  IElementType COMMANDS_LIST = new BashCompositeElementType("COMMANDS_LIST");
  IElementType COMPOUND_LIST = new BashCompositeElementType("COMPOUND_LIST");
  IElementType DO_BLOCK = new BashCompositeElementType("DO_BLOCK");
  IElementType ELIF_CLAUSE = new BashCompositeElementType("ELIF_CLAUSE");
  IElementType FOR_COMMAND = new BashCompositeElementType("FOR_COMMAND");
  IElementType FUNCTION_DEF = new BashCompositeElementType("FUNCTION_DEF");
  IElementType GROUP_COMMAND = new BashCompositeElementType("GROUP_COMMAND");
  IElementType IF_COMMAND = new BashCompositeElementType("IF_COMMAND");
  IElementType LIST = new BashCompositeElementType("LIST");
  IElementType LIST_TERMINATOR = new BashCompositeElementType("LIST_TERMINATOR");
  IElementType PATTERN = new BashCompositeElementType("PATTERN");
  IElementType PATTERN_LIST = new BashCompositeElementType("PATTERN_LIST");
  IElementType PIPELINE = new BashCompositeElementType("PIPELINE");
  IElementType PIPELINE_COMMAND = new BashCompositeElementType("PIPELINE_COMMAND");
  IElementType REDIRECTION = new BashCompositeElementType("REDIRECTION");
  IElementType REDIRECTION_LIST = new BashCompositeElementType("REDIRECTION_LIST");
  IElementType SELECT_COMMAND = new BashCompositeElementType("SELECT_COMMAND");
  IElementType SHELL_COMMAND = new BashCompositeElementType("SHELL_COMMAND");
  IElementType SIMPLE_COMMAND = new BashCompositeElementType("SIMPLE_COMMAND");
  IElementType SIMPLE_COMMAND_ELEMENT = new BashCompositeElementType("SIMPLE_COMMAND_ELEMENT");
  IElementType STRING = new BashCompositeElementType("STRING");
  IElementType SUBSHELL = new BashCompositeElementType("SUBSHELL");
  IElementType TIMESPEC = new BashCompositeElementType("TIMESPEC");
  IElementType TIME_OPT = new BashCompositeElementType("TIME_OPT");
  IElementType UNTIL_COMMAND = new BashCompositeElementType("UNTIL_COMMAND");
  IElementType WHILE_COMMAND = new BashCompositeElementType("WHILE_COMMAND");

  IElementType ADD_EQ = new BashTokenType("+=");
  IElementType AMP = new BashTokenType("&");
  IElementType AND_AND = new BashTokenType("&&");
  IElementType ARITH_GE = new BashTokenType(">=");
  IElementType ARITH_GT = new BashTokenType("arith >");
  IElementType ARITH_LE = new BashTokenType("<=");
  IElementType ARITH_LT = new BashTokenType("arith <");
  IElementType ARITH_MINUS = new BashTokenType("-");
  IElementType ARITH_MINUS_MINUS = new BashTokenType("--");
  IElementType ARITH_PLUS = new BashTokenType("+");
  IElementType ARITH_PLUS_PLUS = new BashTokenType("++");
  IElementType ASSIGNMENT_WORD = new BashTokenType("assignment_word");
  IElementType AT = new BashTokenType("@");
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
  IElementType FI = new BashTokenType("fi");
  IElementType FOR = new BashTokenType("for");
  IElementType FUNCTION = new BashTokenType("function");
  IElementType GREATER_THAN = new BashTokenType(">");
  IElementType IF = new BashTokenType("if");
  IElementType INT = new BashTokenType("int");
  IElementType LEFT_CURLY = new BashTokenType("{");
  IElementType LEFT_PAREN = new BashTokenType("(");
  IElementType LEFT_SQUARE = new BashTokenType("[");
  IElementType LESS_THAN = new BashTokenType("<");
  IElementType LINEFEED = new BashTokenType("\\n");
  IElementType MOD = new BashTokenType("%");
  IElementType MULT = new BashTokenType("*");
  IElementType NUMBER = new BashTokenType("number");
  IElementType OR_OR = new BashTokenType("||");
  IElementType PIPE = new BashTokenType("|");
  IElementType PIPE_AMP = new BashTokenType("|&");
  IElementType REDIRECT_GREATER_AMP = new BashTokenType(">&");
  IElementType REDIRECT_GREATER_BAR = new BashTokenType(">|");
  IElementType REDIRECT_HERE_STRING = new BashTokenType("<<<");
  IElementType REDIRECT_LESS_AMP = new BashTokenType("<&");
  IElementType REDIRECT_LESS_GREATER = new BashTokenType("<>");
  IElementType RIGHT_CURLY = new BashTokenType("}");
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
  IElementType UNTIL = new BashTokenType("until");
  IElementType VARIABLE = new BashTokenType("variable");
  IElementType WHILE = new BashTokenType("while");
  IElementType WORD = new BashTokenType("word");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == ASSIGNMENT_WORD_RULE) {
        return new BashAssignmentWordRuleImpl(node);
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
      else if (type == COMPOUND_LIST) {
        return new BashCompoundListImpl(node);
      }
      else if (type == DO_BLOCK) {
        return new BashDoBlockImpl(node);
      }
      else if (type == ELIF_CLAUSE) {
        return new BashElifClauseImpl(node);
      }
      else if (type == FOR_COMMAND) {
        return new BashForCommandImpl(node);
      }
      else if (type == FUNCTION_DEF) {
        return new BashFunctionDefImpl(node);
      }
      else if (type == GROUP_COMMAND) {
        return new BashGroupCommandImpl(node);
      }
      else if (type == IF_COMMAND) {
        return new BashIfCommandImpl(node);
      }
      else if (type == LIST) {
        return new BashListImpl(node);
      }
      else if (type == LIST_TERMINATOR) {
        return new BashListTerminatorImpl(node);
      }
      else if (type == PATTERN) {
        return new BashPatternImpl(node);
      }
      else if (type == PATTERN_LIST) {
        return new BashPatternListImpl(node);
      }
      else if (type == PIPELINE) {
        return new BashPipelineImpl(node);
      }
      else if (type == PIPELINE_COMMAND) {
        return new BashPipelineCommandImpl(node);
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
      else if (type == SIMPLE_COMMAND) {
        return new BashSimpleCommandImpl(node);
      }
      else if (type == SIMPLE_COMMAND_ELEMENT) {
        return new BashSimpleCommandElementImpl(node);
      }
      else if (type == STRING) {
        return new BashStringImpl(node);
      }
      else if (type == SUBSHELL) {
        return new BashSubshellImpl(node);
      }
      else if (type == TIMESPEC) {
        return new BashTimespecImpl(node);
      }
      else if (type == TIME_OPT) {
        return new BashTimeOptImpl(node);
      }
      else if (type == UNTIL_COMMAND) {
        return new BashUntilCommandImpl(node);
      }
      else if (type == WHILE_COMMAND) {
        return new BashWhileCommandImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
