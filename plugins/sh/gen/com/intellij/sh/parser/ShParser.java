// This is a generated file. Not intended for manual editing.
package com.intellij.sh.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.intellij.sh.ShTypes.*;
import static com.intellij.sh.parser.ShParserUtil.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class ShParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType t, PsiBuilder b) {
    parseLight(t, b);
    return b.getTreeBuilt();
  }

  public void parseLight(IElementType t, PsiBuilder b) {
    boolean r;
    b = adapt_builder_(t, b, this, EXTENDS_SETS_);
    Marker m = enter_section_(b, 0, _COLLAPSE_, null);
    r = parse_root_(t, b);
    exit_section_(b, 0, m, t, r, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType t, PsiBuilder b) {
    return parse_root_(t, b, 0);
  }

  static boolean parse_root_(IElementType t, PsiBuilder b, int l) {
    boolean r;
    if (t == BLOCK) {
      r = block(b, l + 1);
    }
    else if (t == DO_BLOCK) {
      r = do_block(b, l + 1);
    }
    else {
      r = file(b, l + 1);
    }
    return r;
  }

  public static final TokenSet[] EXTENDS_SETS_ = new TokenSet[] {
    create_token_set_(ARITHMETIC_EXPANSION, OLD_ARITHMETIC_EXPANSION),
    create_token_set_(LITERAL, NUMBER, SIMPLE_COMMAND_ELEMENT, STRING,
      VARIABLE),
    create_token_set_(ARITHMETIC_CONDITION, BINARY_CONDITION, COMPARISON_CONDITION, CONDITION,
      EQUALITY_CONDITION, LITERAL_CONDITION, LOGICAL_AND_CONDITION, LOGICAL_NEGATION_CONDITION,
      LOGICAL_OR_CONDITION, PARENTHESES_CONDITION, REGEX_CONDITION, UNARY_CONDITION),
    create_token_set_(ASSIGNMENT_COMMAND, CASE_COMMAND, COMMAND, COMMAND_SUBSTITUTION_COMMAND,
      CONDITIONAL_COMMAND, EVAL_COMMAND, FOR_COMMAND, FUNCTION_DEFINITION,
      GENERIC_COMMAND_DIRECTIVE, IF_COMMAND, INCLUDE_COMMAND, INCLUDE_DIRECTIVE,
      LET_COMMAND, PIPELINE_COMMAND, SELECT_COMMAND, SHELL_COMMAND,
      SIMPLE_COMMAND, SUBSHELL_COMMAND, TEST_COMMAND, UNTIL_COMMAND,
      WHILE_COMMAND),
    create_token_set_(ADD_EXPRESSION, ARRAY_EXPRESSION, ASSIGNMENT_EXPRESSION, BITWISE_AND_EXPRESSION,
      BITWISE_EXCLUSIVE_OR_EXPRESSION, BITWISE_OR_EXPRESSION, BITWISE_SHIFT_EXPRESSION, COMMA_EXPRESSION,
      COMPARISON_EXPRESSION, CONDITIONAL_EXPRESSION, EQUALITY_EXPRESSION, EXPRESSION,
      EXP_EXPRESSION, INDEX_EXPRESSION, LITERAL_EXPRESSION, LOGICAL_AND_EXPRESSION,
      LOGICAL_BITWISE_NEGATION_EXPRESSION, LOGICAL_OR_EXPRESSION, MUL_EXPRESSION, PARENTHESES_EXPRESSION,
      POST_EXPRESSION, PRE_EXPRESSION, UNARY_EXPRESSION),
  };

  /* ********************************************************** */
  // block | do_block
  static boolean any_block(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "any_block")) return false;
    if (!nextTokenIs(b, "", DO, LEFT_CURLY)) return false;
    boolean r;
    r = block(b, l + 1);
    if (!r) r = do_block(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // '(' ')'
  static boolean argument_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "argument_list")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeTokens(b, 1, LEFT_PAREN, RIGHT_PAREN);
    p = r; // pin = 1
    exit_section_(b, l, m, r, p, ShParser::argument_list_recover);
    return r || p;
  }

  /* ********************************************************** */
  // !('\n'| '{')
  static boolean argument_list_recover(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "argument_list_recover")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !argument_list_recover_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // '\n'| '{'
  private static boolean argument_list_recover_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "argument_list_recover_0")) return false;
    boolean r;
    r = consumeToken(b, LINEFEED);
    if (!r) r = consumeToken(b, LEFT_CURLY);
    return r;
  }

  /* ********************************************************** */
  // '((' expression? '))'
  public static boolean arithmetic_expansion(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arithmetic_expansion")) return false;
    if (!nextTokenIs(b, LEFT_DOUBLE_PAREN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, ARITHMETIC_EXPANSION, null);
    r = consumeToken(b, LEFT_DOUBLE_PAREN);
    p = r; // pin = 1
    r = r && report_error_(b, arithmetic_expansion_1(b, l + 1));
    r = p && consumeToken(b, RIGHT_DOUBLE_PAREN) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // expression?
  private static boolean arithmetic_expansion_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arithmetic_expansion_1")) return false;
    expression(b, l + 1, -1);
    return true;
  }

  /* ********************************************************** */
  // '((' arithmetic_for_expression '))' list_terminator? newlines
  static boolean arithmetic_for_clause(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arithmetic_for_clause")) return false;
    if (!nextTokenIs(b, LEFT_DOUBLE_PAREN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, LEFT_DOUBLE_PAREN);
    p = r; // pin = 1
    r = r && report_error_(b, arithmetic_for_expression(b, l + 1));
    r = p && report_error_(b, consumeToken(b, RIGHT_DOUBLE_PAREN)) && r;
    r = p && report_error_(b, arithmetic_for_clause_3(b, l + 1)) && r;
    r = p && newlines(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // list_terminator?
  private static boolean arithmetic_for_clause_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arithmetic_for_clause_3")) return false;
    list_terminator(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // expression? ';' newlines expression? ';' newlines expression?
  static boolean arithmetic_for_expression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arithmetic_for_expression")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_);
    r = arithmetic_for_expression_0(b, l + 1);
    r = r && consumeToken(b, SEMI);
    r = r && newlines(b, l + 1);
    r = r && arithmetic_for_expression_3(b, l + 1);
    r = r && consumeToken(b, SEMI);
    r = r && newlines(b, l + 1);
    r = r && arithmetic_for_expression_6(b, l + 1);
    exit_section_(b, l, m, r, false, ShParser::arithmetic_for_expression_recover);
    return r;
  }

  // expression?
  private static boolean arithmetic_for_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arithmetic_for_expression_0")) return false;
    expression(b, l + 1, -1);
    return true;
  }

  // expression?
  private static boolean arithmetic_for_expression_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arithmetic_for_expression_3")) return false;
    expression(b, l + 1, -1);
    return true;
  }

  // expression?
  private static boolean arithmetic_for_expression_6(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arithmetic_for_expression_6")) return false;
    expression(b, l + 1, -1);
    return true;
  }

  /* ********************************************************** */
  // !('))')
  static boolean arithmetic_for_expression_recover(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arithmetic_for_expression_recover")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !consumeToken(b, RIGHT_DOUBLE_PAREN);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // newlines '='? expression newlines
  public static boolean array_assignment(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "array_assignment")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ARRAY_ASSIGNMENT, "<array assignment>");
    r = newlines(b, l + 1);
    r = r && array_assignment_1(b, l + 1);
    r = r && expression(b, l + 1, -1);
    r = r && newlines(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // '='?
  private static boolean array_assignment_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "array_assignment_1")) return false;
    consumeToken(b, ASSIGN);
    return true;
  }

  /* ********************************************************** */
  // array_expression? ('='|"+=") [assignment_list | <<parseUntilSpace (literal | composed_var)>>]
  //                             {'=' <<parseUntilSpace literal >>}*
  public static boolean assignment_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "assignment_command")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _LEFT_, ASSIGNMENT_COMMAND, "<assignment command>");
    r = assignment_command_0(b, l + 1);
    r = r && assignment_command_1(b, l + 1);
    r = r && assignment_command_2(b, l + 1);
    r = r && assignment_command_3(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // array_expression?
  private static boolean assignment_command_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "assignment_command_0")) return false;
    array_expression(b, l + 1);
    return true;
  }

  // '='|"+="
  private static boolean assignment_command_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "assignment_command_1")) return false;
    boolean r;
    r = consumeToken(b, ASSIGN);
    if (!r) r = consumeToken(b, PLUS_ASSIGN);
    return r;
  }

  // [assignment_list | <<parseUntilSpace (literal | composed_var)>>]
  private static boolean assignment_command_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "assignment_command_2")) return false;
    assignment_command_2_0(b, l + 1);
    return true;
  }

  // assignment_list | <<parseUntilSpace (literal | composed_var)>>
  private static boolean assignment_command_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "assignment_command_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = assignment_list(b, l + 1);
    if (!r) r = parseUntilSpace(b, l + 1, ShParser::assignment_command_2_0_1_0);
    exit_section_(b, m, null, r);
    return r;
  }

  // literal | composed_var
  private static boolean assignment_command_2_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "assignment_command_2_0_1_0")) return false;
    boolean r;
    r = literal(b, l + 1);
    if (!r) r = composed_var(b, l + 1);
    return r;
  }

  // {'=' <<parseUntilSpace literal >>}*
  private static boolean assignment_command_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "assignment_command_3")) return false;
    while (true) {
      int c = current_position_(b);
      if (!assignment_command_3_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "assignment_command_3", c)) break;
    }
    return true;
  }

  // '=' <<parseUntilSpace literal >>
  private static boolean assignment_command_3_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "assignment_command_3_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ASSIGN);
    r = r && parseUntilSpace(b, l + 1, ShParser::literal);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '(' (<<backslash>> | array_assignment)* ')'
  public static boolean assignment_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "assignment_list")) return false;
    if (!nextTokenIs(b, LEFT_PAREN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, ASSIGNMENT_LIST, null);
    r = consumeToken(b, LEFT_PAREN);
    p = r; // pin = 1
    r = r && report_error_(b, assignment_list_1(b, l + 1));
    r = p && consumeToken(b, RIGHT_PAREN) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (<<backslash>> | array_assignment)*
  private static boolean assignment_list_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "assignment_list_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!assignment_list_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "assignment_list_1", c)) break;
    }
    return true;
  }

  // <<backslash>> | array_assignment
  private static boolean assignment_list_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "assignment_list_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = backslash(b, l + 1);
    if (!r) r = array_assignment(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '{' block_compound_list '}'
  public static boolean block(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block")) return false;
    if (!nextTokenIs(b, LEFT_CURLY)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, BLOCK, null);
    r = consumeToken(b, LEFT_CURLY);
    p = r; // pin = 1
    r = r && report_error_(b, block_compound_list(b, l + 1));
    r = p && consumeToken(b, RIGHT_CURLY) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // (nl pipeline_command_list | pipeline_command_list) end_of_list newlines
  public static boolean block_compound_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_compound_list")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, COMPOUND_LIST, "<block compound list>");
    r = block_compound_list_0(b, l + 1);
    p = r; // pin = 1
    r = r && report_error_(b, end_of_list(b, l + 1));
    r = p && newlines(b, l + 1) && r;
    exit_section_(b, l, m, r, p, ShParser::block_compound_list_recover);
    return r || p;
  }

  // nl pipeline_command_list | pipeline_command_list
  private static boolean block_compound_list_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_compound_list_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = block_compound_list_0_0(b, l + 1);
    if (!r) r = pipeline_command_list(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // nl pipeline_command_list
  private static boolean block_compound_list_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_compound_list_0_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = nl(b, l + 1);
    p = r; // pin = 1
    r = r && pipeline_command_list(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // !('{' | '\n' | '}' | do | done)
  static boolean block_compound_list_recover(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_compound_list_recover")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !block_compound_list_recover_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // '{' | '\n' | '}' | do | done
  private static boolean block_compound_list_recover_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_compound_list_recover_0")) return false;
    boolean r;
    r = consumeToken(b, LEFT_CURLY);
    if (!r) r = consumeToken(b, LINEFEED);
    if (!r) r = consumeToken(b, RIGHT_CURLY);
    if (!r) r = consumeToken(b, DO);
    if (!r) r = consumeToken(b, DONE);
    return r;
  }

  /* ********************************************************** */
  // '{' (word | brace_expansion)* '}'
  public static boolean brace_expansion(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "brace_expansion")) return false;
    if (!nextTokenIs(b, LEFT_CURLY)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LEFT_CURLY);
    r = r && brace_expansion_1(b, l + 1);
    r = r && consumeToken(b, RIGHT_CURLY);
    exit_section_(b, m, BRACE_EXPANSION, r);
    return r;
  }

  // (word | brace_expansion)*
  private static boolean brace_expansion_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "brace_expansion_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!brace_expansion_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "brace_expansion_1", c)) break;
    }
    return true;
  }

  // word | brace_expansion
  private static boolean brace_expansion_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "brace_expansion_1_0")) return false;
    boolean r;
    r = consumeToken(b, WORD);
    if (!r) r = brace_expansion(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // newlines pattern ')' newlines compound_case_list?
  public static boolean case_clause(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_clause")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, CASE_CLAUSE, "<case clause>");
    r = newlines(b, l + 1);
    r = r && pattern(b, l + 1);
    r = r && consumeToken(b, RIGHT_PAREN);
    p = r; // pin = 3
    r = r && report_error_(b, newlines(b, l + 1));
    r = p && case_clause_4(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // compound_case_list?
  private static boolean case_clause_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_clause_4")) return false;
    compound_case_list(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // case_clause (';;' (case_clause | newlines))*
  static boolean case_clause_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_clause_list")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = case_clause(b, l + 1);
    p = r; // pin = 1
    r = r && case_clause_list_1(b, l + 1);
    exit_section_(b, l, m, r, p, ShParser::case_clause_recover);
    return r || p;
  }

  // (';;' (case_clause | newlines))*
  private static boolean case_clause_list_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_clause_list_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!case_clause_list_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "case_clause_list_1", c)) break;
    }
    return true;
  }

  // ';;' (case_clause | newlines)
  private static boolean case_clause_list_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_clause_list_1_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, CASE_END);
    p = r; // pin = 1
    r = r && case_clause_list_1_0_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // case_clause | newlines
  private static boolean case_clause_list_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_clause_list_1_0_1")) return false;
    boolean r;
    r = case_clause(b, l + 1);
    if (!r) r = newlines(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // !(esac)
  static boolean case_clause_recover(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_clause_recover")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !consumeToken(b, ESAC);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // case w+ in case_clause_list esac
  public static boolean case_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_command")) return false;
    if (!nextTokenIs(b, CASE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, CASE_COMMAND, null);
    r = consumeToken(b, CASE);
    p = r; // pin = 1
    r = r && report_error_(b, case_command_1(b, l + 1));
    r = p && report_error_(b, consumeToken(b, IN)) && r;
    r = p && report_error_(b, case_clause_list(b, l + 1)) && r;
    r = p && consumeToken(b, ESAC) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // w+
  private static boolean case_command_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_command_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = w(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!w(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "case_command_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // !(')'|';;'|esac)
  static boolean case_pattern_recover(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_pattern_recover")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !case_pattern_recover_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // ')'|';;'|esac
  private static boolean case_pattern_recover_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_pattern_recover_0")) return false;
    boolean r;
    r = consumeToken(b, RIGHT_PAREN);
    if (!r) r = consumeToken(b, CASE_END);
    if (!r) r = consumeToken(b, ESAC);
    return r;
  }

  /* ********************************************************** */
  // shell_command [heredoc | redirection+]
  //           | include_command
  //           | simple_command
  public static boolean command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "command")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, COMMAND, "<command>");
    r = command_0(b, l + 1);
    if (!r) r = include_command(b, l + 1);
    if (!r) r = simple_command(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // shell_command [heredoc | redirection+]
  private static boolean command_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "command_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = shell_command(b, l + 1);
    r = r && command_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // [heredoc | redirection+]
  private static boolean command_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "command_0_1")) return false;
    command_0_1_0(b, l + 1);
    return true;
  }

  // heredoc | redirection+
  private static boolean command_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "command_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = heredoc(b, l + 1);
    if (!r) r = command_0_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // redirection+
  private static boolean command_0_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "command_0_1_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = redirection(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!redirection(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "command_0_1_0_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // !<<isModeOn "BACKQUOTE">> OPEN_BACKQUOTE <<withOn "BACKQUOTE" list?>> CLOSE_BACKQUOTE
  public static boolean command_substitution_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "command_substitution_command")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, COMMAND_SUBSTITUTION_COMMAND, "<command substitution command>");
    r = command_substitution_command_0(b, l + 1);
    r = r && consumeToken(b, OPEN_BACKQUOTE);
    p = r; // pin = 2
    r = r && report_error_(b, withOn(b, l + 1, "BACKQUOTE", ShParser::command_substitution_command_2_1));
    r = p && consumeToken(b, CLOSE_BACKQUOTE) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // !<<isModeOn "BACKQUOTE">>
  private static boolean command_substitution_command_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "command_substitution_command_0")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !isModeOn(b, l + 1, "BACKQUOTE");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // list?
  private static boolean command_substitution_command_2_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "command_substitution_command_2_1")) return false;
    list(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // pipeline_command (
  //                      ('&&' | '||') newlines? pipeline_command
  //                    | ('|' | '|&' | '&'| ';') pipeline_command?
  //                  )*
  public static boolean commands_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commands_list")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, COMMANDS_LIST, "<commands list>");
    r = pipeline_command(b, l + 1);
    p = r; // pin = 1
    r = r && commands_list_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (
  //                      ('&&' | '||') newlines? pipeline_command
  //                    | ('|' | '|&' | '&'| ';') pipeline_command?
  //                  )*
  private static boolean commands_list_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commands_list_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!commands_list_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "commands_list_1", c)) break;
    }
    return true;
  }

  // ('&&' | '||') newlines? pipeline_command
  //                    | ('|' | '|&' | '&'| ';') pipeline_command?
  private static boolean commands_list_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commands_list_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = commands_list_1_0_0(b, l + 1);
    if (!r) r = commands_list_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // ('&&' | '||') newlines? pipeline_command
  private static boolean commands_list_1_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commands_list_1_0_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = commands_list_1_0_0_0(b, l + 1);
    p = r; // pin = 1
    r = r && report_error_(b, commands_list_1_0_0_1(b, l + 1));
    r = p && pipeline_command(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // '&&' | '||'
  private static boolean commands_list_1_0_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commands_list_1_0_0_0")) return false;
    boolean r;
    r = consumeToken(b, AND_AND);
    if (!r) r = consumeToken(b, OR_OR);
    return r;
  }

  // newlines?
  private static boolean commands_list_1_0_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commands_list_1_0_0_1")) return false;
    newlines(b, l + 1);
    return true;
  }

  // ('|' | '|&' | '&'| ';') pipeline_command?
  private static boolean commands_list_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commands_list_1_0_1")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = commands_list_1_0_1_0(b, l + 1);
    p = r; // pin = 1
    r = r && commands_list_1_0_1_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // '|' | '|&' | '&'| ';'
  private static boolean commands_list_1_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commands_list_1_0_1_0")) return false;
    boolean r;
    r = consumeToken(b, PIPE);
    if (!r) r = consumeToken(b, PIPE_AMP);
    if (!r) r = consumeToken(b, AMP);
    if (!r) r = consumeToken(b, SEMI);
    return r;
  }

  // pipeline_command?
  private static boolean commands_list_1_0_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commands_list_1_0_1_1")) return false;
    pipeline_command(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // '$' &('(' | '((' | '{') composed_var_inner
  static boolean composed_var(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "composed_var")) return false;
    if (!nextTokenIs(b, DOLLAR)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, DOLLAR);
    r = r && composed_var_1(b, l + 1);
    p = r; // pin = 2
    r = r && composed_var_inner(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // &('(' | '((' | '{')
  private static boolean composed_var_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "composed_var_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = composed_var_1_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // '(' | '((' | '{'
  private static boolean composed_var_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "composed_var_1_0")) return false;
    boolean r;
    r = consumeToken(b, LEFT_PAREN);
    if (!r) r = consumeToken(b, LEFT_DOUBLE_PAREN);
    if (!r) r = consumeToken(b, LEFT_CURLY);
    return r;
  }

  /* ********************************************************** */
  // arithmetic_expansion
  //                               | subshell_command
  //                               | shell_parameter_expansion
  static boolean composed_var_inner(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "composed_var_inner")) return false;
    boolean r;
    r = arithmetic_expansion(b, l + 1);
    if (!r) r = subshell_command(b, l + 1);
    if (!r) r = shell_parameter_expansion(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // pipeline_command_list end_of_list? newlines
  public static boolean compound_case_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "compound_case_list")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, COMPOUND_LIST, "<compound case list>");
    r = pipeline_command_list(b, l + 1);
    p = r; // pin = 1
    r = r && report_error_(b, compound_case_list_1(b, l + 1));
    r = p && newlines(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // end_of_list?
  private static boolean compound_case_list_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "compound_case_list_1")) return false;
    end_of_list(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // condition
  static boolean compound_condition(PsiBuilder b, int l) {
    return condition(b, l + 1, -1);
  }

  /* ********************************************************** */
  // newlines pipeline_command_list end_of_list newlines
  public static boolean compound_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "compound_list")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, COMPOUND_LIST, "<compound list>");
    r = newlines(b, l + 1);
    r = r && pipeline_command_list(b, l + 1);
    p = r; // pin = 2
    r = r && report_error_(b, end_of_list(b, l + 1));
    r = p && newlines(b, l + 1) && r;
    exit_section_(b, l, m, r, p, ShParser::compound_list_recover);
    return r || p;
  }

  /* ********************************************************** */
  // !(elif | else | then | fi)
  static boolean compound_list_recover(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "compound_list_recover")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !compound_list_recover_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // elif | else | then | fi
  private static boolean compound_list_recover_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "compound_list_recover_0")) return false;
    boolean r;
    r = consumeToken(b, ELIF);
    if (!r) r = consumeToken(b, ELSE);
    if (!r) r = consumeToken(b, THEN);
    if (!r) r = consumeToken(b, FI);
    return r;
  }

  /* ********************************************************** */
  // '[[' compound_condition (']]'|']'  <<differentBracketsWarning>>)
  //                        | '['  test_condition? ( ']'|']]' <<differentBracketsWarning>>)
  public static boolean conditional_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "conditional_command")) return false;
    if (!nextTokenIs(b, "<conditional command>", LEFT_DOUBLE_BRACKET, LEFT_SQUARE)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, CONDITIONAL_COMMAND, "<conditional command>");
    r = conditional_command_0(b, l + 1);
    if (!r) r = conditional_command_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // '[[' compound_condition (']]'|']'  <<differentBracketsWarning>>)
  private static boolean conditional_command_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "conditional_command_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, LEFT_DOUBLE_BRACKET);
    p = r; // pin = 1
    r = r && report_error_(b, compound_condition(b, l + 1));
    r = p && conditional_command_0_2(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // ']]'|']'  <<differentBracketsWarning>>
  private static boolean conditional_command_0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "conditional_command_0_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, RIGHT_DOUBLE_BRACKET);
    if (!r) r = conditional_command_0_2_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // ']'  <<differentBracketsWarning>>
  private static boolean conditional_command_0_2_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "conditional_command_0_2_1")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, RIGHT_SQUARE);
    p = r; // pin = 1
    r = r && differentBracketsWarning(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // '['  test_condition? ( ']'|']]' <<differentBracketsWarning>>)
  private static boolean conditional_command_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "conditional_command_1")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, LEFT_SQUARE);
    p = r; // pin = 1
    r = r && report_error_(b, conditional_command_1_1(b, l + 1));
    r = p && conditional_command_1_2(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // test_condition?
  private static boolean conditional_command_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "conditional_command_1_1")) return false;
    test_condition(b, l + 1);
    return true;
  }

  // ']'|']]' <<differentBracketsWarning>>
  private static boolean conditional_command_1_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "conditional_command_1_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, RIGHT_SQUARE);
    if (!r) r = conditional_command_1_2_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // ']]' <<differentBracketsWarning>>
  private static boolean conditional_command_1_2_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "conditional_command_1_2_1")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, RIGHT_DOUBLE_BRACKET);
    p = r; // pin = 1
    r = r && differentBracketsWarning(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // do  block_compound_list done
  public static boolean do_block(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "do_block")) return false;
    if (!nextTokenIs(b, DO)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, DO_BLOCK, null);
    r = consumeToken(b, DO);
    p = r; // pin = 1
    r = r && report_error_(b, block_compound_list(b, l + 1));
    r = p && consumeToken(b, DONE) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // elif compound_list then_clause
  public static boolean elif_clause(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "elif_clause")) return false;
    if (!nextTokenIs(b, ELIF)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, ELIF_CLAUSE, null);
    r = consumeToken(b, ELIF);
    p = r; // pin = 1
    r = r && report_error_(b, compound_list(b, l + 1));
    r = p && then_clause(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // else compound_list
  public static boolean else_clause(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "else_clause")) return false;
    if (!nextTokenIs(b, ELSE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, ELSE_CLAUSE, null);
    r = consumeToken(b, ELSE);
    p = r; // pin = 1
    r = r && compound_list(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // '\n' | ';' | '&'
  static boolean end_of_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "end_of_list")) return false;
    boolean r;
    r = consumeToken(b, LINEFEED);
    if (!r) r = consumeToken(b, SEMI);
    if (!r) r = consumeToken(b, AMP);
    return r;
  }

  /* ********************************************************** */
  // eval (EVAL_CONTENT|simple_command_element)*
  public static boolean eval_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "eval_command")) return false;
    if (!nextTokenIs(b, EVAL)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, EVAL_COMMAND, null);
    r = consumeToken(b, EVAL);
    p = r; // pin = 1
    r = r && eval_command_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (EVAL_CONTENT|simple_command_element)*
  private static boolean eval_command_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "eval_command_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!eval_command_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "eval_command_1", c)) break;
    }
    return true;
  }

  // EVAL_CONTENT|simple_command_element
  private static boolean eval_command_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "eval_command_1_0")) return false;
    boolean r;
    r = consumeToken(b, EVAL_CONTENT);
    if (!r) r = simple_command_element(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // shebang? newlines
  //   simple_list
  static boolean file(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "file")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = file_0(b, l + 1);
    r = r && newlines(b, l + 1);
    r = r && simple_list(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // shebang?
  private static boolean file_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "file_0")) return false;
    consumeToken(b, SHEBANG);
    return true;
  }

  /* ********************************************************** */
  // arithmetic_for_clause
  //               | in_for_clause
  public static boolean for_clause(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_clause")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FOR_CLAUSE, "<for clause>");
    r = arithmetic_for_clause(b, l + 1);
    if (!r) r = in_for_clause(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // for for_clause any_block
  public static boolean for_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_command")) return false;
    if (!nextTokenIs(b, FOR)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, FOR_COMMAND, null);
    r = consumeToken(b, FOR);
    p = r; // pin = 1
    r = r && report_error_(b, for_clause(b, l + 1));
    r = p && any_block(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // &(function | word '(') function_definition_inner
  public static boolean function_definition(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_definition")) return false;
    if (!nextTokenIs(b, "<function definition>", FUNCTION, WORD)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, FUNCTION_DEFINITION, "<function definition>");
    r = function_definition_0(b, l + 1);
    r = r && function_definition_inner(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // &(function | word '(')
  private static boolean function_definition_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_definition_0")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = function_definition_0_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // function | word '('
  private static boolean function_definition_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_definition_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, FUNCTION);
    if (!r) r = parseTokens(b, 0, WORD, LEFT_PAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // word argument_list newlines block
  //                                      | function (word | <<functionNameKeywordsRemapped>>) argument_list? newlines block
  static boolean function_definition_inner(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_definition_inner")) return false;
    if (!nextTokenIs(b, "", FUNCTION, WORD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = function_definition_inner_0(b, l + 1);
    if (!r) r = function_definition_inner_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // word argument_list newlines block
  private static boolean function_definition_inner_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_definition_inner_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, WORD);
    r = r && argument_list(b, l + 1);
    p = r; // pin = function|argument_list
    r = r && report_error_(b, newlines(b, l + 1));
    r = p && block(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // function (word | <<functionNameKeywordsRemapped>>) argument_list? newlines block
  private static boolean function_definition_inner_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_definition_inner_1")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, FUNCTION);
    p = r; // pin = function|argument_list
    r = r && report_error_(b, function_definition_inner_1_1(b, l + 1));
    r = p && report_error_(b, function_definition_inner_1_2(b, l + 1)) && r;
    r = p && report_error_(b, newlines(b, l + 1)) && r;
    r = p && block(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // word | <<functionNameKeywordsRemapped>>
  private static boolean function_definition_inner_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_definition_inner_1_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, WORD);
    if (!r) r = functionNameKeywordsRemapped(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // argument_list?
  private static boolean function_definition_inner_1_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_definition_inner_1_2")) return false;
    argument_list(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // simple_command_element_inner
  public static boolean generic_command_directive(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "generic_command_directive")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, GENERIC_COMMAND_DIRECTIVE, "<generic command directive>");
    r = simple_command_element_inner(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // HEREDOC_MARKER_TAG HEREDOC_MARKER_START [commands_list heredoc_pipeline_separator? | heredoc_pipeline_separator commands_list?] newlines
  //             HEREDOC_CONTENT*
  //             (HEREDOC_MARKER_END | <<eof>>)
  public static boolean heredoc(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "heredoc")) return false;
    if (!nextTokenIs(b, HEREDOC_MARKER_TAG)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START);
    r = r && heredoc_2(b, l + 1);
    r = r && newlines(b, l + 1);
    r = r && heredoc_4(b, l + 1);
    r = r && heredoc_5(b, l + 1);
    exit_section_(b, m, HEREDOC, r);
    return r;
  }

  // [commands_list heredoc_pipeline_separator? | heredoc_pipeline_separator commands_list?]
  private static boolean heredoc_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "heredoc_2")) return false;
    heredoc_2_0(b, l + 1);
    return true;
  }

  // commands_list heredoc_pipeline_separator? | heredoc_pipeline_separator commands_list?
  private static boolean heredoc_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "heredoc_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = heredoc_2_0_0(b, l + 1);
    if (!r) r = heredoc_2_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // commands_list heredoc_pipeline_separator?
  private static boolean heredoc_2_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "heredoc_2_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = commands_list(b, l + 1);
    r = r && heredoc_2_0_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // heredoc_pipeline_separator?
  private static boolean heredoc_2_0_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "heredoc_2_0_0_1")) return false;
    heredoc_pipeline_separator(b, l + 1);
    return true;
  }

  // heredoc_pipeline_separator commands_list?
  private static boolean heredoc_2_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "heredoc_2_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = heredoc_pipeline_separator(b, l + 1);
    r = r && heredoc_2_0_1_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // commands_list?
  private static boolean heredoc_2_0_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "heredoc_2_0_1_1")) return false;
    commands_list(b, l + 1);
    return true;
  }

  // HEREDOC_CONTENT*
  private static boolean heredoc_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "heredoc_4")) return false;
    while (true) {
      int c = current_position_(b);
      if (!consumeToken(b, HEREDOC_CONTENT)) break;
      if (!empty_element_parsed_guard_(b, "heredoc_4", c)) break;
    }
    return true;
  }

  // HEREDOC_MARKER_END | <<eof>>
  private static boolean heredoc_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "heredoc_5")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, HEREDOC_MARKER_END);
    if (!r) r = eof(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '&&' | '||' | '&' | '|' | '|&' | ';'
  static boolean heredoc_pipeline_separator(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "heredoc_pipeline_separator")) return false;
    boolean r;
    r = consumeToken(b, AND_AND);
    if (!r) r = consumeToken(b, OR_OR);
    if (!r) r = consumeToken(b, AMP);
    if (!r) r = consumeToken(b, PIPE);
    if (!r) r = consumeToken(b, PIPE_AMP);
    if (!r) r = consumeToken(b, SEMI);
    return r;
  }

  /* ********************************************************** */
  // if compound_list then_clause
  //                elif_clause*
  //                else_clause?
  //                fi
  public static boolean if_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "if_command")) return false;
    if (!nextTokenIs(b, IF)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, IF_COMMAND, null);
    r = consumeToken(b, IF);
    p = r; // pin = 1
    r = r && report_error_(b, compound_list(b, l + 1));
    r = p && report_error_(b, then_clause(b, l + 1)) && r;
    r = p && report_error_(b, if_command_3(b, l + 1)) && r;
    r = p && report_error_(b, if_command_4(b, l + 1)) && r;
    r = p && consumeToken(b, FI) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // elif_clause*
  private static boolean if_command_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "if_command_3")) return false;
    while (true) {
      int c = current_position_(b);
      if (!elif_clause(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "if_command_3", c)) break;
    }
    return true;
  }

  // else_clause?
  private static boolean if_command_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "if_command_4")) return false;
    else_clause(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // "in" w+ list_terminator newlines
  static boolean in_clause(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "in_clause")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, "in");
    p = r; // pin = 1
    r = r && report_error_(b, in_clause_1(b, l + 1));
    r = p && report_error_(b, list_terminator(b, l + 1)) && r;
    r = p && newlines(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // w+
  private static boolean in_clause_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "in_clause_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = w(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!w(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "in_clause_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // w (';' newlines | newlines in_clause?)
  static boolean in_for_clause(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "in_for_clause")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = w(b, l + 1);
    p = r; // pin = 1
    r = r && in_for_clause_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // ';' newlines | newlines in_clause?
  private static boolean in_for_clause_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "in_for_clause_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = in_for_clause_1_0(b, l + 1);
    if (!r) r = in_for_clause_1_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // ';' newlines
  private static boolean in_for_clause_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "in_for_clause_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SEMI);
    r = r && newlines(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // newlines in_clause?
  private static boolean in_for_clause_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "in_for_clause_1_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = newlines(b, l + 1);
    r = r && in_for_clause_1_1_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // in_clause?
  private static boolean in_for_clause_1_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "in_for_clause_1_1_1")) return false;
    in_clause(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // include_directive (simple_command_element | <<keywordsRemapped>>)+
  public static boolean include_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "include_command")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, INCLUDE_COMMAND, "<include command>");
    r = include_directive(b, l + 1);
    r = r && include_command_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (simple_command_element | <<keywordsRemapped>>)+
  private static boolean include_command_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "include_command_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = include_command_1_0(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!include_command_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "include_command_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // simple_command_element | <<keywordsRemapped>>
  private static boolean include_command_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "include_command_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = simple_command_element(b, l + 1);
    if (!r) r = keywordsRemapped(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // &('source' | '.') word
  public static boolean include_directive(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "include_directive")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, INCLUDE_DIRECTIVE, "<include directive>");
    r = include_directive_0(b, l + 1);
    r = r && consumeToken(b, WORD);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // &('source' | '.')
  private static boolean include_directive_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "include_directive_0")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = include_directive_0_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // 'source' | '.'
  private static boolean include_directive_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "include_directive_0_0")) return false;
    boolean r;
    r = consumeToken(b, "source");
    if (!r) r = consumeToken(b, ".");
    return r;
  }

  /* ********************************************************** */
  // let expression
  public static boolean let_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "let_command")) return false;
    if (!nextTokenIs(b, LET)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, LET_COMMAND, null);
    r = consumeToken(b, LET);
    p = r; // pin = 1
    r = r && expression(b, l + 1, -1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // newlines pipeline_command_list end_of_list? newlines
  public static boolean list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "list")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, COMPOUND_LIST, "<list>");
    r = newlines(b, l + 1);
    r = r && pipeline_command_list(b, l + 1);
    p = r; // pin = 2
    r = r && report_error_(b, list_2(b, l + 1));
    r = p && newlines(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // end_of_list?
  private static boolean list_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "list_2")) return false;
    end_of_list(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // '\n' | ';'
  public static boolean list_terminator(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "list_terminator")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, LIST_TERMINATOR, "<list terminator>");
    r = consumeToken(b, LINEFEED);
    if (!r) r = consumeToken(b, SEMI);
    exit_section_(b, l, m, r, false, ShParser::list_terminator_recover);
    return r;
  }

  /* ********************************************************** */
  // !(do | '{' | '\n')
  static boolean list_terminator_recover(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "list_terminator_recover")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !list_terminator_recover_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // do | '{' | '\n'
  private static boolean list_terminator_recover_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "list_terminator_recover_0")) return false;
    boolean r;
    r = consumeToken(b, DO);
    if (!r) r = consumeToken(b, LEFT_CURLY);
    if (!r) r = consumeToken(b, LINEFEED);
    return r;
  }

  /* ********************************************************** */
  // word | string | number | variable
  public static boolean literal(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "literal")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, LITERAL, "<literal>");
    r = consumeToken(b, WORD);
    if (!r) r = string(b, l + 1);
    if (!r) r = number(b, l + 1);
    if (!r) r = variable(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // '\n'*
  static boolean newlines(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "newlines")) return false;
    while (true) {
      int c = current_position_(b);
      if (!consumeToken(b, LINEFEED)) break;
      if (!empty_element_parsed_guard_(b, "newlines", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // '\n'+
  static boolean nl(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nl")) return false;
    if (!nextTokenIs(b, LINEFEED)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LINEFEED);
    while (r) {
      int c = current_position_(b);
      if (!consumeToken(b, LINEFEED)) break;
      if (!empty_element_parsed_guard_(b, "nl", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '!' | vars | '$' | brace_expansion | 'file descriptor'
  static boolean not_lvalue(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "not_lvalue")) return false;
    boolean r;
    r = consumeToken(b, BANG);
    if (!r) r = vars(b, l + 1);
    if (!r) r = consumeToken(b, DOLLAR);
    if (!r) r = brace_expansion(b, l + 1);
    if (!r) r = consumeToken(b, FILEDESCRIPTOR);
    return r;
  }

  /* ********************************************************** */
  // int | hex | octal
  public static boolean number(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "number")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, NUMBER, "<number>");
    r = consumeToken(b, INT);
    if (!r) r = consumeToken(b, HEX);
    if (!r) r = consumeToken(b, OCTAL);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // ARITH_SQUARE_LEFT old_arithmetic_expansion_expression? ARITH_SQUARE_RIGHT
  public static boolean old_arithmetic_expansion(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "old_arithmetic_expansion")) return false;
    if (!nextTokenIs(b, ARITH_SQUARE_LEFT)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, OLD_ARITHMETIC_EXPANSION, null);
    r = consumeToken(b, ARITH_SQUARE_LEFT);
    p = r; // pin = 1
    r = r && report_error_(b, old_arithmetic_expansion_1(b, l + 1));
    r = p && consumeToken(b, ARITH_SQUARE_RIGHT) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // old_arithmetic_expansion_expression?
  private static boolean old_arithmetic_expansion_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "old_arithmetic_expansion_1")) return false;
    old_arithmetic_expansion_expression(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // expression
  static boolean old_arithmetic_expansion_expression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "old_arithmetic_expansion_expression")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_);
    r = expression(b, l + 1, -1);
    exit_section_(b, l, m, r, false, ShParser::old_arithmetic_expansion_expression_recover);
    return r;
  }

  /* ********************************************************** */
  // !(ARITH_SQUARE_RIGHT)
  static boolean old_arithmetic_expansion_expression_recover(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "old_arithmetic_expansion_expression_recover")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !consumeToken(b, ARITH_SQUARE_RIGHT);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // '('? w+ ('|' w+)*
  public static boolean pattern(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, PATTERN, "<pattern>");
    r = pattern_0(b, l + 1);
    p = r; // pin = 1
    r = r && report_error_(b, pattern_1(b, l + 1));
    r = p && pattern_2(b, l + 1) && r;
    exit_section_(b, l, m, r, p, ShParser::case_pattern_recover);
    return r || p;
  }

  // '('?
  private static boolean pattern_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern_0")) return false;
    consumeToken(b, LEFT_PAREN);
    return true;
  }

  // w+
  private static boolean pattern_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = w(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!w(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "pattern_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // ('|' w+)*
  private static boolean pattern_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern_2")) return false;
    while (true) {
      int c = current_position_(b);
      if (!pattern_2_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "pattern_2", c)) break;
    }
    return true;
  }

  // '|' w+
  private static boolean pattern_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern_2_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, PIPE);
    p = r; // pin = 1
    r = r && pattern_2_0_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // w+
  private static boolean pattern_2_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern_2_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = w(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!w(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "pattern_2_0_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '!'? command
  //                     | '!'? eval_command
  //                     | '!'? test_command
  //                     | let_command
  public static boolean pipeline_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_command")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, PIPELINE_COMMAND, "<pipeline command>");
    r = pipeline_command_0(b, l + 1);
    if (!r) r = pipeline_command_1(b, l + 1);
    if (!r) r = pipeline_command_2(b, l + 1);
    if (!r) r = let_command(b, l + 1);
    exit_section_(b, l, m, r, false, ShParser::pipeline_recover);
    return r;
  }

  // '!'? command
  private static boolean pipeline_command_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_command_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = pipeline_command_0_0(b, l + 1);
    r = r && command(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '!'?
  private static boolean pipeline_command_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_command_0_0")) return false;
    consumeToken(b, BANG);
    return true;
  }

  // '!'? eval_command
  private static boolean pipeline_command_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_command_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = pipeline_command_1_0(b, l + 1);
    r = r && eval_command(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '!'?
  private static boolean pipeline_command_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_command_1_0")) return false;
    consumeToken(b, BANG);
    return true;
  }

  // '!'? test_command
  private static boolean pipeline_command_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_command_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = pipeline_command_2_0(b, l + 1);
    r = r && test_command(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '!'?
  private static boolean pipeline_command_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_command_2_0")) return false;
    consumeToken(b, BANG);
    return true;
  }

  /* ********************************************************** */
  // pipeline_command (pipeline_command_list_separator pipeline_command)*
  static boolean pipeline_command_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_command_list")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = pipeline_command(b, l + 1);
    p = r; // pin = 1
    r = r && pipeline_command_list_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (pipeline_command_list_separator pipeline_command)*
  private static boolean pipeline_command_list_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_command_list_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!pipeline_command_list_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "pipeline_command_list_1", c)) break;
    }
    return true;
  }

  // pipeline_command_list_separator pipeline_command
  private static boolean pipeline_command_list_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_command_list_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = pipeline_command_list_separator(b, l + 1);
    r = r && pipeline_command(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // ('&&'|  '||' |  '&' |  ';' | '|' | '\n') newlines
  static boolean pipeline_command_list_separator(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_command_list_separator")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = pipeline_command_list_separator_0(b, l + 1);
    r = r && newlines(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '&&'|  '||' |  '&' |  ';' | '|' | '\n'
  private static boolean pipeline_command_list_separator_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_command_list_separator_0")) return false;
    boolean r;
    r = consumeToken(b, AND_AND);
    if (!r) r = consumeToken(b, OR_OR);
    if (!r) r = consumeToken(b, AMP);
    if (!r) r = consumeToken(b, SEMI);
    if (!r) r = consumeToken(b, PIPE);
    if (!r) r = consumeToken(b, LINEFEED);
    return r;
  }

  /* ********************************************************** */
  // !(case|if|while|until|select|'{'|function|'$'|'&&'|';'|';;'|'||'|'&'|'!'|'['|'[['|'('|')'|'|'|'|&'|'\n'|'(('|
  //                                var | word|EXPR_CONDITIONAL_LEFT|ARITH_SQUARE_LEFT | CLOSE_BACKQUOTE | do | done | '}')
  static boolean pipeline_recover(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_recover")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !pipeline_recover_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // case|if|while|until|select|'{'|function|'$'|'&&'|';'|';;'|'||'|'&'|'!'|'['|'[['|'('|')'|'|'|'|&'|'\n'|'(('|
  //                                var | word|EXPR_CONDITIONAL_LEFT|ARITH_SQUARE_LEFT | CLOSE_BACKQUOTE | do | done | '}'
  private static boolean pipeline_recover_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_recover_0")) return false;
    boolean r;
    r = consumeToken(b, CASE);
    if (!r) r = consumeToken(b, IF);
    if (!r) r = consumeToken(b, WHILE);
    if (!r) r = consumeToken(b, UNTIL);
    if (!r) r = consumeToken(b, SELECT);
    if (!r) r = consumeToken(b, LEFT_CURLY);
    if (!r) r = consumeToken(b, FUNCTION);
    if (!r) r = consumeToken(b, DOLLAR);
    if (!r) r = consumeToken(b, AND_AND);
    if (!r) r = consumeToken(b, SEMI);
    if (!r) r = consumeToken(b, CASE_END);
    if (!r) r = consumeToken(b, OR_OR);
    if (!r) r = consumeToken(b, AMP);
    if (!r) r = consumeToken(b, BANG);
    if (!r) r = consumeToken(b, LEFT_SQUARE);
    if (!r) r = consumeToken(b, LEFT_DOUBLE_BRACKET);
    if (!r) r = consumeToken(b, LEFT_PAREN);
    if (!r) r = consumeToken(b, RIGHT_PAREN);
    if (!r) r = consumeToken(b, PIPE);
    if (!r) r = consumeToken(b, PIPE_AMP);
    if (!r) r = consumeToken(b, LINEFEED);
    if (!r) r = consumeToken(b, LEFT_DOUBLE_PAREN);
    if (!r) r = consumeToken(b, VAR);
    if (!r) r = consumeToken(b, WORD);
    if (!r) r = consumeToken(b, EXPR_CONDITIONAL_LEFT);
    if (!r) r = consumeToken(b, ARITH_SQUARE_LEFT);
    if (!r) r = consumeToken(b, CLOSE_BACKQUOTE);
    if (!r) r = consumeToken(b, DO);
    if (!r) r = consumeToken(b, DONE);
    if (!r) r = consumeToken(b, RIGHT_CURLY);
    return r;
  }

  /* ********************************************************** */
  // ('<(' | '>(') list ')'
  public static boolean process_substitution(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "process_substitution")) return false;
    if (!nextTokenIs(b, "<process substitution>", INPUT_PROCESS_SUBSTITUTION, OUTPUT_PROCESS_SUBSTITUTION)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, PROCESS_SUBSTITUTION, "<process substitution>");
    r = process_substitution_0(b, l + 1);
    p = r; // pin = 1
    r = r && report_error_(b, list(b, l + 1));
    r = p && consumeToken(b, RIGHT_PAREN) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // '<(' | '>('
  private static boolean process_substitution_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "process_substitution_0")) return false;
    boolean r;
    r = consumeToken(b, INPUT_PROCESS_SUBSTITUTION);
    if (!r) r = consumeToken(b, OUTPUT_PROCESS_SUBSTITUTION);
    return r;
  }

  /* ********************************************************** */
  // redirection_inner | '&>' w | number redirection_inner | process_substitution
  public static boolean redirection(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, REDIRECTION, "<redirection>");
    r = redirection_inner(b, l + 1);
    if (!r) r = redirection_1(b, l + 1);
    if (!r) r = redirection_2(b, l + 1);
    if (!r) r = process_substitution(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // '&>' w
  private static boolean redirection_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, REDIRECT_AMP_GREATER);
    r = r && w(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // number redirection_inner
  private static boolean redirection_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = number(b, l + 1);
    r = r && redirection_inner(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // ('<&' | '>&') (number | '-')
  //                             | ('>' | '<' | '>>' | '<<<' | '<<' | '<&' | '>&' | '&>>' | '<>' | '>|') redirection_target
  static boolean redirection_inner(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_inner")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = redirection_inner_0(b, l + 1);
    if (!r) r = redirection_inner_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // ('<&' | '>&') (number | '-')
  private static boolean redirection_inner_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_inner_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = redirection_inner_0_0(b, l + 1);
    r = r && redirection_inner_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '<&' | '>&'
  private static boolean redirection_inner_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_inner_0_0")) return false;
    boolean r;
    r = consumeToken(b, REDIRECT_LESS_AMP);
    if (!r) r = consumeToken(b, REDIRECT_GREATER_AMP);
    return r;
  }

  // number | '-'
  private static boolean redirection_inner_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_inner_0_1")) return false;
    boolean r;
    r = number(b, l + 1);
    if (!r) r = consumeToken(b, MINUS);
    return r;
  }

  // ('>' | '<' | '>>' | '<<<' | '<<' | '<&' | '>&' | '&>>' | '<>' | '>|') redirection_target
  private static boolean redirection_inner_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_inner_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = redirection_inner_1_0(b, l + 1);
    r = r && redirection_target(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '>' | '<' | '>>' | '<<<' | '<<' | '<&' | '>&' | '&>>' | '<>' | '>|'
  private static boolean redirection_inner_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_inner_1_0")) return false;
    boolean r;
    r = consumeToken(b, GT);
    if (!r) r = consumeToken(b, LT);
    if (!r) r = consumeToken(b, SHIFT_RIGHT);
    if (!r) r = consumeToken(b, REDIRECT_HERE_STRING);
    if (!r) r = consumeToken(b, SHIFT_LEFT);
    if (!r) r = consumeToken(b, REDIRECT_LESS_AMP);
    if (!r) r = consumeToken(b, REDIRECT_GREATER_AMP);
    if (!r) r = consumeToken(b, REDIRECT_AMP_GREATER_GREATER);
    if (!r) r = consumeToken(b, REDIRECT_LESS_GREATER);
    if (!r) r = consumeToken(b, REDIRECT_GREATER_BAR);
    return r;
  }

  /* ********************************************************** */
  // process_substitution | <<parseUntilSpace w>>
  static boolean redirection_target(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_target")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, null, "<redirection target>");
    r = process_substitution(b, l + 1);
    if (!r) r = parseUntilSpace(b, l + 1, ShParser::w);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // w+
  public static boolean regex_pattern(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "regex_pattern")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, REGEX_PATTERN, "<regex pattern>");
    r = w(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!w(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "regex_pattern", c)) break;
    }
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // select w select_expression newlines any_block
  public static boolean select_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "select_command")) return false;
    if (!nextTokenIs(b, SELECT)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, SELECT_COMMAND, null);
    r = consumeToken(b, SELECT);
    p = r; // pin = 1
    r = r && report_error_(b, w(b, l + 1));
    r = p && report_error_(b, select_expression(b, l + 1)) && r;
    r = p && report_error_(b, newlines(b, l + 1)) && r;
    r = p && any_block(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // in_clause | list_terminator?
  static boolean select_expression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "select_expression")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = in_clause(b, l + 1);
    if (!r) r = select_expression_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // list_terminator?
  private static boolean select_expression_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "select_expression_1")) return false;
    list_terminator(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // if_command
  //                   | for_command
  //                   | case_command
  //                   | while_command
  //                   | until_command
  //                   | select_command
  //                   | subshell_command
  //                   | block
  //                   | function_definition
  public static boolean shell_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "shell_command")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, SHELL_COMMAND, "<shell command>");
    r = if_command(b, l + 1);
    if (!r) r = for_command(b, l + 1);
    if (!r) r = case_command(b, l + 1);
    if (!r) r = while_command(b, l + 1);
    if (!r) r = until_command(b, l + 1);
    if (!r) r = select_command(b, l + 1);
    if (!r) r = subshell_command(b, l + 1);
    if (!r) r = block(b, l + 1);
    if (!r) r = function_definition(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // '{' shell_parameter_expansion_inner+ '}'
  public static boolean shell_parameter_expansion(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "shell_parameter_expansion")) return false;
    if (!nextTokenIs(b, LEFT_CURLY)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, SHELL_PARAMETER_EXPANSION, null);
    r = consumeToken(b, LEFT_CURLY);
    p = r; // pin = 1
    r = r && report_error_(b, shell_parameter_expansion_1(b, l + 1));
    r = p && consumeToken(b, RIGHT_CURLY) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // shell_parameter_expansion_inner+
  private static boolean shell_parameter_expansion_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "shell_parameter_expansion_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = shell_parameter_expansion_inner(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!shell_parameter_expansion_inner(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "shell_parameter_expansion_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // w | array_expression | param_separator | vars
  static boolean shell_parameter_expansion_inner(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "shell_parameter_expansion_inner")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, null, "<parameter expansion>");
    r = w(b, l + 1);
    if (!r) r = array_expression(b, l + 1);
    if (!r) r = consumeToken(b, PARAM_SEPARATOR);
    if (!r) r = vars(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // generic_command_directive (simple_command_element | <<keywordsRemapped>>)*
  public static boolean simple_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simple_command")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _COLLAPSE_, SIMPLE_COMMAND, "<simple command>");
    r = generic_command_directive(b, l + 1);
    p = r; // pin = 1
    r = r && simple_command_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (simple_command_element | <<keywordsRemapped>>)*
  private static boolean simple_command_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simple_command_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!simple_command_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "simple_command_1", c)) break;
    }
    return true;
  }

  // simple_command_element | <<keywordsRemapped>>
  private static boolean simple_command_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simple_command_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = simple_command_element(b, l + 1);
    if (!r) r = keywordsRemapped(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // simple_command_element_inner
  public static boolean simple_command_element(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simple_command_element")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, SIMPLE_COMMAND_ELEMENT, "<simple command element>");
    r = simple_command_element_inner(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // literal assignment_command?
  //                                         | not_lvalue
  //                                         | redirection
  //                                         | composed_var
  //                                         | heredoc
  //                                         | conditional_command
  //                                         | command_substitution_command
  //                                         | arithmetic_expansion
  //                                         | old_arithmetic_expansion
  static boolean simple_command_element_inner(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simple_command_element_inner")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = simple_command_element_inner_0(b, l + 1);
    if (!r) r = not_lvalue(b, l + 1);
    if (!r) r = redirection(b, l + 1);
    if (!r) r = composed_var(b, l + 1);
    if (!r) r = heredoc(b, l + 1);
    if (!r) r = conditional_command(b, l + 1);
    if (!r) r = command_substitution_command(b, l + 1);
    if (!r) r = arithmetic_expansion(b, l + 1);
    if (!r) r = old_arithmetic_expansion(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // literal assignment_command?
  private static boolean simple_command_element_inner_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simple_command_element_inner_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = literal(b, l + 1);
    p = r; // pin = 1
    r = r && simple_command_element_inner_0_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // assignment_command?
  private static boolean simple_command_element_inner_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simple_command_element_inner_0_1")) return false;
    assignment_command(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // (commands_list newlines)*
  static boolean simple_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simple_list")) return false;
    while (true) {
      int c = current_position_(b);
      if (!simple_list_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "simple_list", c)) break;
    }
    return true;
  }

  // commands_list newlines
  private static boolean simple_list_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simple_list_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = commands_list(b, l + 1);
    p = r; // pin = 1
    r = r && newlines(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // OPEN_QUOTE (STRING_CONTENT | vars | <<notQuote>>)* CLOSE_QUOTE | RAW_STRING
  public static boolean string(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "string")) return false;
    if (!nextTokenIs(b, "<string>", OPEN_QUOTE, RAW_STRING)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, STRING, "<string>");
    r = string_0(b, l + 1);
    if (!r) r = consumeToken(b, RAW_STRING);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // OPEN_QUOTE (STRING_CONTENT | vars | <<notQuote>>)* CLOSE_QUOTE
  private static boolean string_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "string_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, OPEN_QUOTE);
    p = r; // pin = 1
    r = r && report_error_(b, string_0_1(b, l + 1));
    r = p && consumeToken(b, CLOSE_QUOTE) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (STRING_CONTENT | vars | <<notQuote>>)*
  private static boolean string_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "string_0_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!string_0_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "string_0_1", c)) break;
    }
    return true;
  }

  // STRING_CONTENT | vars | <<notQuote>>
  private static boolean string_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "string_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, STRING_CONTENT);
    if (!r) r = vars(b, l + 1);
    if (!r) r = notQuote(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '(' list ')'
  public static boolean subshell_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "subshell_command")) return false;
    if (!nextTokenIs(b, LEFT_PAREN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, SUBSHELL_COMMAND, null);
    r = consumeToken(b, LEFT_PAREN);
    p = r; // pin = 1
    r = r && report_error_(b, list(b, l + 1));
    r = p && consumeToken(b, RIGHT_PAREN) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // test simple_command_element*
  public static boolean test_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "test_command")) return false;
    if (!nextTokenIs(b, TEST)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, TEST_COMMAND, null);
    r = consumeToken(b, TEST);
    p = r; // pin = 1
    r = r && test_command_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // simple_command_element*
  private static boolean test_command_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "test_command_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!simple_command_element(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "test_command_1", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // condition redirection?
  static boolean test_condition(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "test_condition")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = condition(b, l + 1, -1);
    r = r && test_condition_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // redirection?
  private static boolean test_condition_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "test_condition_1")) return false;
    redirection(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // then compound_list
  public static boolean then_clause(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "then_clause")) return false;
    if (!nextTokenIs(b, THEN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, THEN_CLAUSE, null);
    r = consumeToken(b, THEN);
    p = r; // pin = 1
    r = r && compound_list(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // '-a' | '-b' | '-c'| '-d' | '-e' | '-f' | '-g' | '-h'
  //                    | '-k' | '-n' | '-o' | '-p'| '-r' | '-s' | '-t' | '-u'
  //                    | '-v' | '-w' | '-x' | '-z'
  //                    | '-G' | '-L' | '-N' | '-O' | '-R' | '-S'
  static boolean unary_op(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unary_op")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, null, "<conditional operator>");
    r = consumeToken(b, "-a");
    if (!r) r = consumeToken(b, "-b");
    if (!r) r = consumeToken(b, "-c");
    if (!r) r = consumeToken(b, "-d");
    if (!r) r = consumeToken(b, "-e");
    if (!r) r = consumeToken(b, "-f");
    if (!r) r = consumeToken(b, "-g");
    if (!r) r = consumeToken(b, "-h");
    if (!r) r = consumeToken(b, "-k");
    if (!r) r = consumeToken(b, "-n");
    if (!r) r = consumeToken(b, "-o");
    if (!r) r = consumeToken(b, "-p");
    if (!r) r = consumeToken(b, "-r");
    if (!r) r = consumeToken(b, "-s");
    if (!r) r = consumeToken(b, "-t");
    if (!r) r = consumeToken(b, "-u");
    if (!r) r = consumeToken(b, "-v");
    if (!r) r = consumeToken(b, "-w");
    if (!r) r = consumeToken(b, "-x");
    if (!r) r = consumeToken(b, "-z");
    if (!r) r = consumeToken(b, "-G");
    if (!r) r = consumeToken(b, "-L");
    if (!r) r = consumeToken(b, "-N");
    if (!r) r = consumeToken(b, "-O");
    if (!r) r = consumeToken(b, "-R");
    if (!r) r = consumeToken(b, "-S");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // until block_compound_list do_block
  public static boolean until_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "until_command")) return false;
    if (!nextTokenIs(b, UNTIL)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, UNTIL_COMMAND, null);
    r = consumeToken(b, UNTIL);
    p = r; // pin = 1
    r = r && report_error_(b, block_compound_list(b, l + 1));
    r = p && do_block(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // var
  public static boolean variable(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "variable")) return false;
    if (!nextTokenIs(b, VAR)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, VAR);
    exit_section_(b, m, VARIABLE, r);
    return r;
  }

  /* ********************************************************** */
  // variable | composed_var | command_substitution_command | old_arithmetic_expansion
  static boolean vars(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "vars")) return false;
    boolean r;
    r = variable(b, l + 1);
    if (!r) r = composed_var(b, l + 1);
    if (!r) r = command_substitution_command(b, l + 1);
    if (!r) r = old_arithmetic_expansion(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // word | string | number | not_lvalue
  static boolean w(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "w")) return false;
    boolean r;
    r = consumeToken(b, WORD);
    if (!r) r = string(b, l + 1);
    if (!r) r = number(b, l + 1);
    if (!r) r = not_lvalue(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // while block_compound_list do_block
  public static boolean while_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "while_command")) return false;
    if (!nextTokenIs(b, WHILE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, WHILE_COMMAND, null);
    r = consumeToken(b, WHILE);
    p = r; // pin = 1
    r = r && report_error_(b, block_compound_list(b, l + 1));
    r = p && do_block(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // Expression root: condition
  // Operator priority table:
  // 0: BINARY(logical_or_condition)
  // 1: BINARY(logical_and_condition)
  // 2: PREFIX(logical_negation_condition)
  // 3: BINARY(equality_condition)
  // 4: POSTFIX(regex_condition)
  // 5: BINARY(comparison_condition)
  // 6: ATOM(arithmetic_condition)
  // 7: ATOM(binary_condition)
  // 8: ATOM(unary_condition)
  // 9: ATOM(literal_condition)
  // 10: PREFIX(parentheses_condition)
  public static boolean condition(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "condition")) return false;
    addVariant(b, "<condition>");
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, "<condition>");
    r = logical_negation_condition(b, l + 1);
    if (!r) r = arithmetic_condition(b, l + 1);
    if (!r) r = binary_condition(b, l + 1);
    if (!r) r = unary_condition(b, l + 1);
    if (!r) r = literal_condition(b, l + 1);
    if (!r) r = parentheses_condition(b, l + 1);
    p = r;
    r = r && condition_0(b, l + 1, g);
    exit_section_(b, l, m, null, r, p, null);
    return r || p;
  }

  public static boolean condition_0(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "condition_0")) return false;
    boolean r = true;
    while (true) {
      Marker m = enter_section_(b, l, _LEFT_, null);
      if (g < 0 && logical_or_condition_0(b, l + 1)) {
        r = condition(b, l, 0);
        exit_section_(b, l, m, LOGICAL_OR_CONDITION, r, true, null);
      }
      else if (g < 1 && logical_and_condition_0(b, l + 1)) {
        r = condition(b, l, 1);
        exit_section_(b, l, m, LOGICAL_AND_CONDITION, r, true, null);
      }
      else if (g < 3 && equality_condition_0(b, l + 1)) {
        r = condition(b, l, 3);
        exit_section_(b, l, m, EQUALITY_CONDITION, r, true, null);
      }
      else if (g < 4 && regex_condition_0(b, l + 1)) {
        r = true;
        exit_section_(b, l, m, REGEX_CONDITION, r, true, null);
      }
      else if (g < 5 && comparison_condition_0(b, l + 1)) {
        r = condition(b, l, 5);
        exit_section_(b, l, m, COMPARISON_CONDITION, r, true, null);
      }
      else {
        exit_section_(b, l, m, null, false, false, null);
        break;
      }
    }
    return r;
  }

  // newlines ('||' | '-o') newlines
  private static boolean logical_or_condition_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "logical_or_condition_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = newlines(b, l + 1);
    r = r && logical_or_condition_0_1(b, l + 1);
    r = r && newlines(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '||' | '-o'
  private static boolean logical_or_condition_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "logical_or_condition_0_1")) return false;
    boolean r;
    r = consumeTokenSmart(b, OR_OR);
    if (!r) r = consumeTokenSmart(b, "-o");
    return r;
  }

  // newlines ('&&' | '-a') newlines
  private static boolean logical_and_condition_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "logical_and_condition_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = newlines(b, l + 1);
    r = r && logical_and_condition_0_1(b, l + 1);
    r = r && newlines(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '&&' | '-a'
  private static boolean logical_and_condition_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "logical_and_condition_0_1")) return false;
    boolean r;
    r = consumeTokenSmart(b, AND_AND);
    if (!r) r = consumeTokenSmart(b, "-a");
    return r;
  }

  public static boolean logical_negation_condition(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "logical_negation_condition")) return false;
    if (!nextTokenIsSmart(b, BANG)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = logical_negation_condition_0(b, l + 1);
    p = r;
    r = p && condition(b, l, 2);
    exit_section_(b, l, m, LOGICAL_NEGATION_CONDITION, r, p, null);
    return r || p;
  }

  // '!' <<space>>
  private static boolean logical_negation_condition_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "logical_negation_condition_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, BANG);
    r = r && space(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '=' | '==' | '!='
  private static boolean equality_condition_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "equality_condition_0")) return false;
    boolean r;
    r = consumeTokenSmart(b, ASSIGN);
    if (!r) r = consumeTokenSmart(b, EQ);
    if (!r) r = consumeTokenSmart(b, NE);
    return r;
  }

  // '=~' regex_pattern
  private static boolean regex_condition_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "regex_condition_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, REGEXP);
    r = r && regex_pattern(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '<' | '>'
  private static boolean comparison_condition_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "comparison_condition_0")) return false;
    boolean r;
    r = consumeTokenSmart(b, LT);
    if (!r) r = consumeTokenSmart(b, GT);
    return r;
  }

  // expression ('-eq' | '-ne' | '-lt' | '-le' | '-gt' | '-ge') expression
  public static boolean arithmetic_condition(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arithmetic_condition")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ARITHMETIC_CONDITION, "<arithmetic condition>");
    r = expression(b, l + 1, -1);
    r = r && arithmetic_condition_1(b, l + 1);
    r = r && expression(b, l + 1, -1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // '-eq' | '-ne' | '-lt' | '-le' | '-gt' | '-ge'
  private static boolean arithmetic_condition_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arithmetic_condition_1")) return false;
    boolean r;
    r = consumeTokenSmart(b, "-eq");
    if (!r) r = consumeTokenSmart(b, "-ne");
    if (!r) r = consumeTokenSmart(b, "-lt");
    if (!r) r = consumeTokenSmart(b, "-le");
    if (!r) r = consumeTokenSmart(b, "-gt");
    if (!r) r = consumeTokenSmart(b, "-ge");
    return r;
  }

  // <<parseUntilSpace w>> ('-ef' | '-nt' | '-ot' | '-eq' | '-ne' | '-lt' | '-le' | '-gt' | '-ge') <<parseUntilSpace w>>
  public static boolean binary_condition(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "binary_condition")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, BINARY_CONDITION, "<binary condition>");
    r = parseUntilSpace(b, l + 1, ShParser::w);
    r = r && binary_condition_1(b, l + 1);
    r = r && parseUntilSpace(b, l + 1, ShParser::w);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // '-ef' | '-nt' | '-ot' | '-eq' | '-ne' | '-lt' | '-le' | '-gt' | '-ge'
  private static boolean binary_condition_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "binary_condition_1")) return false;
    boolean r;
    r = consumeTokenSmart(b, "-ef");
    if (!r) r = consumeTokenSmart(b, "-nt");
    if (!r) r = consumeTokenSmart(b, "-ot");
    if (!r) r = consumeTokenSmart(b, "-eq");
    if (!r) r = consumeTokenSmart(b, "-ne");
    if (!r) r = consumeTokenSmart(b, "-lt");
    if (!r) r = consumeTokenSmart(b, "-le");
    if (!r) r = consumeTokenSmart(b, "-gt");
    if (!r) r = consumeTokenSmart(b, "-ge");
    return r;
  }

  // unary_op <<parseUntilSpace w>>
  public static boolean unary_condition(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unary_condition")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, UNARY_CONDITION, "<unary condition>");
    r = unary_op(b, l + 1);
    r = r && parseUntilSpace(b, l + 1, ShParser::w);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // <<parseUntilSpace w>> newlines | newlines <<parseUntilSpace w>>
  public static boolean literal_condition(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "literal_condition")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, LITERAL_CONDITION, "<literal condition>");
    r = literal_condition_0(b, l + 1);
    if (!r) r = literal_condition_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // <<parseUntilSpace w>> newlines
  private static boolean literal_condition_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "literal_condition_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = parseUntilSpace(b, l + 1, ShParser::w);
    r = r && newlines(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // newlines <<parseUntilSpace w>>
  private static boolean literal_condition_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "literal_condition_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = newlines(b, l + 1);
    r = r && parseUntilSpace(b, l + 1, ShParser::w);
    exit_section_(b, m, null, r);
    return r;
  }

  public static boolean parentheses_condition(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parentheses_condition")) return false;
    if (!nextTokenIsSmart(b, LEFT_PAREN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = consumeTokenSmart(b, LEFT_PAREN);
    p = r;
    r = p && condition(b, l, -1);
    r = p && report_error_(b, consumeToken(b, RIGHT_PAREN)) && r;
    exit_section_(b, l, m, PARENTHESES_CONDITION, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // Expression root: expression
  // Operator priority table:
  // 0: BINARY(comma_expression)
  // 1: BINARY(assignment_expression)
  // 2: BINARY(conditional_expression)
  // 3: BINARY(logical_or_expression)
  // 4: BINARY(logical_and_expression)
  // 5: BINARY(bitwise_or_expression)
  // 6: BINARY(bitwise_exclusive_or_expression)
  // 7: BINARY(bitwise_and_expression)
  // 8: BINARY(equality_expression)
  // 9: BINARY(comparison_expression)
  // 10: BINARY(bitwise_shift_expression)
  // 11: BINARY(add_expression)
  // 12: BINARY(mul_expression)
  // 13: BINARY(exp_expression)
  // 14: PREFIX(logical_bitwise_negation_expression)
  // 15: PREFIX(unary_expression)
  // 16: PREFIX(pre_expression)
  // 17: POSTFIX(post_expression)
  // 18: BINARY(index_expression)
  // 19: PREFIX(array_expression)
  // 20: ATOM(literal_expression)
  // 21: ATOM(parentheses_expression)
  public static boolean expression(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "expression")) return false;
    addVariant(b, "<expression>");
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, "<expression>");
    r = logical_bitwise_negation_expression(b, l + 1);
    if (!r) r = unary_expression(b, l + 1);
    if (!r) r = pre_expression(b, l + 1);
    if (!r) r = array_expression(b, l + 1);
    if (!r) r = literal_expression(b, l + 1);
    if (!r) r = parentheses_expression(b, l + 1);
    p = r;
    r = r && expression_0(b, l + 1, g);
    exit_section_(b, l, m, null, r, p, null);
    return r || p;
  }

  public static boolean expression_0(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "expression_0")) return false;
    boolean r = true;
    while (true) {
      Marker m = enter_section_(b, l, _LEFT_, null);
      if (g < 0 && consumeTokenSmart(b, COMMA)) {
        r = expression(b, l, 0);
        exit_section_(b, l, m, COMMA_EXPRESSION, r, true, null);
      }
      else if (g < 1 && assignment_expression_0(b, l + 1)) {
        r = expression(b, l, 1);
        exit_section_(b, l, m, ASSIGNMENT_EXPRESSION, r, true, null);
      }
      else if (g < 2 && consumeTokenSmart(b, QMARK)) {
        r = report_error_(b, expression(b, l, 2));
        r = conditional_expression_1(b, l + 1) && r;
        exit_section_(b, l, m, CONDITIONAL_EXPRESSION, r, true, null);
      }
      else if (g < 3 && consumeTokenSmart(b, OR_OR)) {
        r = expression(b, l, 3);
        exit_section_(b, l, m, LOGICAL_OR_EXPRESSION, r, true, null);
      }
      else if (g < 4 && consumeTokenSmart(b, AND_AND)) {
        r = expression(b, l, 4);
        exit_section_(b, l, m, LOGICAL_AND_EXPRESSION, r, true, null);
      }
      else if (g < 5 && consumeTokenSmart(b, PIPE)) {
        r = expression(b, l, 5);
        exit_section_(b, l, m, BITWISE_OR_EXPRESSION, r, true, null);
      }
      else if (g < 6 && consumeTokenSmart(b, XOR)) {
        r = expression(b, l, 6);
        exit_section_(b, l, m, BITWISE_EXCLUSIVE_OR_EXPRESSION, r, true, null);
      }
      else if (g < 7 && consumeTokenSmart(b, AMP)) {
        r = expression(b, l, 7);
        exit_section_(b, l, m, BITWISE_AND_EXPRESSION, r, true, null);
      }
      else if (g < 8 && equality_expression_0(b, l + 1)) {
        r = expression(b, l, 8);
        exit_section_(b, l, m, EQUALITY_EXPRESSION, r, true, null);
      }
      else if (g < 9 && comparison_expression_0(b, l + 1)) {
        r = expression(b, l, 9);
        exit_section_(b, l, m, COMPARISON_EXPRESSION, r, true, null);
      }
      else if (g < 10 && bitwise_shift_expression_0(b, l + 1)) {
        r = expression(b, l, 10);
        exit_section_(b, l, m, BITWISE_SHIFT_EXPRESSION, r, true, null);
      }
      else if (g < 11 && add_expression_0(b, l + 1)) {
        r = expression(b, l, 11);
        exit_section_(b, l, m, ADD_EXPRESSION, r, true, null);
      }
      else if (g < 12 && mul_expression_0(b, l + 1)) {
        r = expression(b, l, 12);
        exit_section_(b, l, m, MUL_EXPRESSION, r, true, null);
      }
      else if (g < 13 && consumeTokenSmart(b, EXPONENT)) {
        r = expression(b, l, 13);
        exit_section_(b, l, m, EXP_EXPRESSION, r, true, null);
      }
      else if (g < 17 && post_expression_0(b, l + 1)) {
        r = true;
        exit_section_(b, l, m, POST_EXPRESSION, r, true, null);
      }
      else if (g < 18 && consumeTokenSmart(b, LEFT_SQUARE)) {
        r = report_error_(b, expression(b, l, 18));
        r = consumeToken(b, RIGHT_SQUARE) && r;
        exit_section_(b, l, m, INDEX_EXPRESSION, r, true, null);
      }
      else {
        exit_section_(b, l, m, null, false, false, null);
        break;
      }
    }
    return r;
  }

  // '=' |'*=' |'/=' |'%=' |'+=' |'-=' |'<<=' |'>>=' |'&=' |'^=' |'|='
  private static boolean assignment_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "assignment_expression_0")) return false;
    boolean r;
    r = consumeTokenSmart(b, ASSIGN);
    if (!r) r = consumeTokenSmart(b, MULT_ASSIGN);
    if (!r) r = consumeTokenSmart(b, DIV_ASSIGN);
    if (!r) r = consumeTokenSmart(b, MOD_ASSIGN);
    if (!r) r = consumeTokenSmart(b, PLUS_ASSIGN);
    if (!r) r = consumeTokenSmart(b, MINUS_ASSIGN);
    if (!r) r = consumeTokenSmart(b, SHIFT_LEFT_ASSIGN);
    if (!r) r = consumeTokenSmart(b, SHIFT_RIGHT_ASSIGN);
    if (!r) r = consumeTokenSmart(b, BIT_AND_ASSIGN);
    if (!r) r = consumeTokenSmart(b, BIT_XOR_ASSIGN);
    if (!r) r = consumeTokenSmart(b, BIT_OR_ASSIGN);
    return r;
  }

  // ':' expression
  private static boolean conditional_expression_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "conditional_expression_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COLON);
    r = r && expression(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '==' | '!='
  private static boolean equality_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "equality_expression_0")) return false;
    boolean r;
    r = consumeTokenSmart(b, EQ);
    if (!r) r = consumeTokenSmart(b, NE);
    return r;
  }

  // '<=' | '>=' | '<' | '>'
  private static boolean comparison_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "comparison_expression_0")) return false;
    boolean r;
    r = consumeTokenSmart(b, LE);
    if (!r) r = consumeTokenSmart(b, GE);
    if (!r) r = consumeTokenSmart(b, LT);
    if (!r) r = consumeTokenSmart(b, GT);
    return r;
  }

  // '<<' | '>>'
  private static boolean bitwise_shift_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bitwise_shift_expression_0")) return false;
    boolean r;
    r = consumeTokenSmart(b, SHIFT_LEFT);
    if (!r) r = consumeTokenSmart(b, SHIFT_RIGHT);
    return r;
  }

  // '+' | '-'
  private static boolean add_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "add_expression_0")) return false;
    boolean r;
    r = consumeTokenSmart(b, PLUS);
    if (!r) r = consumeTokenSmart(b, MINUS);
    return r;
  }

  // '*' | '/' | '%'
  private static boolean mul_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "mul_expression_0")) return false;
    boolean r;
    r = consumeTokenSmart(b, MULT);
    if (!r) r = consumeTokenSmart(b, DIV);
    if (!r) r = consumeTokenSmart(b, MOD);
    return r;
  }

  public static boolean logical_bitwise_negation_expression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "logical_bitwise_negation_expression")) return false;
    if (!nextTokenIsSmart(b, BANG, BITWISE_NEGATION)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = logical_bitwise_negation_expression_0(b, l + 1);
    p = r;
    r = p && expression(b, l, 14);
    exit_section_(b, l, m, LOGICAL_BITWISE_NEGATION_EXPRESSION, r, p, null);
    return r || p;
  }

  // '!' | '~'
  private static boolean logical_bitwise_negation_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "logical_bitwise_negation_expression_0")) return false;
    boolean r;
    r = consumeTokenSmart(b, BANG);
    if (!r) r = consumeTokenSmart(b, BITWISE_NEGATION);
    return r;
  }

  public static boolean unary_expression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unary_expression")) return false;
    if (!nextTokenIsSmart(b, MINUS, PLUS)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = unary_expression_0(b, l + 1);
    p = r;
    r = p && expression(b, l, 15);
    exit_section_(b, l, m, UNARY_EXPRESSION, r, p, null);
    return r || p;
  }

  // '-' | '+'
  private static boolean unary_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unary_expression_0")) return false;
    boolean r;
    r = consumeTokenSmart(b, MINUS);
    if (!r) r = consumeTokenSmart(b, PLUS);
    return r;
  }

  public static boolean pre_expression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pre_expression")) return false;
    if (!nextTokenIsSmart(b, MINUS_MINUS, PLUS_PLUS)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = pre_expression_0(b, l + 1);
    p = r;
    r = p && expression(b, l, 16);
    exit_section_(b, l, m, PRE_EXPRESSION, r, p, null);
    return r || p;
  }

  // '--' | '++'
  private static boolean pre_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pre_expression_0")) return false;
    boolean r;
    r = consumeTokenSmart(b, MINUS_MINUS);
    if (!r) r = consumeTokenSmart(b, PLUS_PLUS);
    return r;
  }

  // '--' | '++'
  private static boolean post_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "post_expression_0")) return false;
    boolean r;
    r = consumeTokenSmart(b, MINUS_MINUS);
    if (!r) r = consumeTokenSmart(b, PLUS_PLUS);
    return r;
  }

  public static boolean array_expression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "array_expression")) return false;
    if (!nextTokenIsSmart(b, LEFT_SQUARE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = consumeTokenSmart(b, LEFT_SQUARE);
    p = r;
    r = p && expression(b, l, 19);
    r = p && report_error_(b, consumeToken(b, RIGHT_SQUARE)) && r;
    exit_section_(b, l, m, ARRAY_EXPRESSION, r, p, null);
    return r || p;
  }

  // w+
  public static boolean literal_expression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "literal_expression")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, LITERAL_EXPRESSION, "<literal expression>");
    r = w(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!w(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "literal_expression", c)) break;
    }
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // '(' expression ')'
  public static boolean parentheses_expression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "parentheses_expression")) return false;
    if (!nextTokenIsSmart(b, LEFT_PAREN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, PARENTHESES_EXPRESSION, null);
    r = consumeTokenSmart(b, LEFT_PAREN);
    p = r; // pin = 1
    r = r && report_error_(b, expression(b, l + 1, -1));
    r = p && consumeToken(b, RIGHT_PAREN) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

}
