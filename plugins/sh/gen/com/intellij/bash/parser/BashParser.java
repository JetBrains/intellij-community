// This is a generated file. Not intended for manual editing.
package com.intellij.bash.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.intellij.bash.BashTypes.*;
import static com.intellij.bash.parser.BashParserUtil.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class BashParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType t, PsiBuilder b) {
    parseLight(t, b);
    return b.getTreeBuilt();
  }

  public void parseLight(IElementType t, PsiBuilder b) {
    boolean r;
    b = adapt_builder_(t, b, this, EXTENDS_SETS_);
    Marker m = enter_section_(b, 0, _COLLAPSE_, null);
    if (t == ARITHMETIC_EXPANSION) {
      r = arithmetic_expansion(b, 0);
    }
    else if (t == ARRAY_ASSIGNMENT) {
      r = array_assignment(b, 0);
    }
    else if (t == ASSIGNMENT_COMMAND) {
      r = assignment_command(b, 0);
    }
    else if (t == ASSIGNMENT_LIST) {
      r = assignment_list(b, 0);
    }
    else if (t == BASH_EXPANSION) {
      r = bash_expansion(b, 0);
    }
    else if (t == BLOCK) {
      r = block(b, 0);
    }
    else if (t == CASE_CLAUSE) {
      r = case_clause(b, 0);
    }
    else if (t == CASE_COMMAND) {
      r = case_command(b, 0);
    }
    else if (t == COMMAND) {
      r = command(b, 0);
    }
    else if (t == COMMAND_SUBSTITUTION_COMMAND) {
      r = command_substitution_command(b, 0);
    }
    else if (t == COMMANDS_LIST) {
      r = commands_list(b, 0);
    }
    else if (t == COMPOUND_LIST) {
      r = compound_list(b, 0);
    }
    else if (t == CONDITION) {
      r = condition(b, 0, -1);
    }
    else if (t == CONDITIONAL_COMMAND) {
      r = conditional_command(b, 0);
    }
    else if (t == DO_BLOCK) {
      r = do_block(b, 0);
    }
    else if (t == ELIF_CLAUSE) {
      r = elif_clause(b, 0);
    }
    else if (t == ELSE_CLAUSE) {
      r = else_clause(b, 0);
    }
    else if (t == EXPRESSION) {
      r = expression(b, 0, -1);
    }
    else if (t == FOR_CLAUSE) {
      r = for_clause(b, 0);
    }
    else if (t == FOR_COMMAND) {
      r = for_command(b, 0);
    }
    else if (t == FUNCTION_DEFINITION) {
      r = function_definition(b, 0);
    }
    else if (t == GENERIC_COMMAND_DIRECTIVE) {
      r = generic_command_directive(b, 0);
    }
    else if (t == HEREDOC) {
      r = heredoc(b, 0);
    }
    else if (t == IF_COMMAND) {
      r = if_command(b, 0);
    }
    else if (t == INCLUDE_COMMAND) {
      r = include_command(b, 0);
    }
    else if (t == INCLUDE_DIRECTIVE) {
      r = include_directive(b, 0);
    }
    else if (t == LET_COMMAND) {
      r = let_command(b, 0);
    }
    else if (t == LIST_TERMINATOR) {
      r = list_terminator(b, 0);
    }
    else if (t == LITERAL) {
      r = literal(b, 0);
    }
    else if (t == NUMBER) {
      r = number(b, 0);
    }
    else if (t == OLD_ARITHMETIC_EXPANSION) {
      r = old_arithmetic_expansion(b, 0);
    }
    else if (t == PATTERN) {
      r = pattern(b, 0);
    }
    else if (t == PIPELINE) {
      r = pipeline(b, 0);
    }
    else if (t == PIPELINE_COMMAND) {
      r = pipeline_command(b, 0);
    }
    else if (t == PROCESS_SUBSTITUTION) {
      r = process_substitution(b, 0);
    }
    else if (t == REDIRECTION) {
      r = redirection(b, 0);
    }
    else if (t == REDIRECTION_LIST) {
      r = redirection_list(b, 0);
    }
    else if (t == SELECT_COMMAND) {
      r = select_command(b, 0);
    }
    else if (t == SHELL_COMMAND) {
      r = shell_command(b, 0);
    }
    else if (t == SHELL_PARAMETER_EXPANSION) {
      r = shell_parameter_expansion(b, 0);
    }
    else if (t == SIMPLE_COMMAND) {
      r = simple_command(b, 0);
    }
    else if (t == SIMPLE_COMMAND_ELEMENT) {
      r = simple_command_element(b, 0);
    }
    else if (t == STRING) {
      r = string(b, 0);
    }
    else if (t == SUBSHELL_COMMAND) {
      r = subshell_command(b, 0);
    }
    else if (t == THEN_CLAUSE) {
      r = then_clause(b, 0);
    }
    else if (t == TIME_OPT) {
      r = time_opt(b, 0);
    }
    else if (t == TIMESPEC) {
      r = timespec(b, 0);
    }
    else if (t == TRAP_COMMAND) {
      r = trap_command(b, 0);
    }
    else if (t == UNTIL_COMMAND) {
      r = until_command(b, 0);
    }
    else if (t == VARIABLE) {
      r = variable(b, 0);
    }
    else if (t == WHILE_COMMAND) {
      r = while_command(b, 0);
    }
    else {
      r = parse_root_(t, b, 0);
    }
    exit_section_(b, 0, m, t, r, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType t, PsiBuilder b, int l) {
    return file(b, l + 1);
  }

  public static final TokenSet[] EXTENDS_SETS_ = new TokenSet[] {
    create_token_set_(ARITHMETIC_EXPANSION, OLD_ARITHMETIC_EXPANSION),
    create_token_set_(LITERAL, NUMBER, SIMPLE_COMMAND_ELEMENT, STRING,
      VARIABLE),
    create_token_set_(ASSIGNMENT_CONDITION, COMPARISON_CONDITION, CONDITION, EQUALITY_CONDITION,
      LITERAL_CONDITION, LOGICAL_AND_CONDITION, LOGICAL_BITWISE_CONDITION, LOGICAL_OR_CONDITION,
      PARENTHESES_CONDITION),
    create_token_set_(ASSIGNMENT_COMMAND, BLOCK, CASE_COMMAND, COMMAND,
      COMMAND_SUBSTITUTION_COMMAND, CONDITIONAL_COMMAND, DO_BLOCK, FOR_COMMAND,
      FUNCTION_DEFINITION, GENERIC_COMMAND_DIRECTIVE, IF_COMMAND, INCLUDE_COMMAND,
      INCLUDE_DIRECTIVE, LET_COMMAND, PIPELINE_COMMAND, SELECT_COMMAND,
      SHELL_COMMAND, SIMPLE_COMMAND, SUBSHELL_COMMAND, TRAP_COMMAND,
      UNTIL_COMMAND, WHILE_COMMAND),
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
  // !(do | '{' | '\n')
  static boolean any_block_recover(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "any_block_recover")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !any_block_recover_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // do | '{' | '\n'
  private static boolean any_block_recover_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "any_block_recover_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, DO);
    if (!r) r = consumeToken(b, LEFT_CURLY);
    if (!r) r = consumeToken(b, LINEFEED);
    exit_section_(b, m, null, r);
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
    exit_section_(b, l, m, r, p, argument_list_recover_parser_);
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
    Marker m = enter_section_(b);
    r = consumeToken(b, LINEFEED);
    if (!r) r = consumeToken(b, LEFT_CURLY);
    exit_section_(b, m, null, r);
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
    exit_section_(b, l, m, r, false, arithmetic_for_expression_recover_parser_);
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
    Marker m = enter_section_(b);
    r = consumeToken(b, ASSIGN);
    if (!r) r = consumeToken(b, PLUS_ASSIGN);
    exit_section_(b, m, null, r);
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
    if (!r) r = parseUntilSpace(b, l + 1, assignment_command_2_0_1_0_parser_);
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
    r = r && parseUntilSpace(b, l + 1, literal_parser_);
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
  // '{' (word | bash_expansion)* '}'
  public static boolean bash_expansion(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bash_expansion")) return false;
    if (!nextTokenIs(b, LEFT_CURLY)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LEFT_CURLY);
    r = r && bash_expansion_1(b, l + 1);
    r = r && consumeToken(b, RIGHT_CURLY);
    exit_section_(b, m, BASH_EXPANSION, r);
    return r;
  }

  // (word | bash_expansion)*
  private static boolean bash_expansion_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bash_expansion_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!bash_expansion_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "bash_expansion_1", c)) break;
    }
    return true;
  }

  // word | bash_expansion
  private static boolean bash_expansion_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bash_expansion_1_0")) return false;
    boolean r;
    r = consumeToken(b, WORD);
    if (!r) r = bash_expansion(b, l + 1);
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
  // (nl block_pipeline_command_list|block_pipeline_command_list) end_of_list  newlines
  public static boolean block_compound_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_compound_list")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, COMPOUND_LIST, "<block compound list>");
    r = block_compound_list_0(b, l + 1);
    p = r; // pin = 1
    r = r && report_error_(b, end_of_list(b, l + 1));
    r = p && newlines(b, l + 1) && r;
    exit_section_(b, l, m, r, p, block_compound_list_recover_parser_);
    return r || p;
  }

  // nl block_pipeline_command_list|block_pipeline_command_list
  private static boolean block_compound_list_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_compound_list_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = block_compound_list_0_0(b, l + 1);
    if (!r) r = block_pipeline_command_list(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // nl block_pipeline_command_list
  private static boolean block_compound_list_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_compound_list_0_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = nl(b, l + 1);
    p = r; // pin = 1
    r = r && block_pipeline_command_list(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // !('{' | '\n' | '}')
  static boolean block_compound_list_recover(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_compound_list_recover")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !block_compound_list_recover_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // '{' | '\n' | '}'
  private static boolean block_compound_list_recover_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_compound_list_recover_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LEFT_CURLY);
    if (!r) r = consumeToken(b, LINEFEED);
    if (!r) r = consumeToken(b, RIGHT_CURLY);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '!'? pipeline
  //                     | timespec '!'? pipeline
  //                     | '!' timespec pipeline
  //                     | trap_command
  //                     | let_command
  public static boolean block_pipeline_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_pipeline_command")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, PIPELINE_COMMAND, "<block pipeline command>");
    r = block_pipeline_command_0(b, l + 1);
    if (!r) r = block_pipeline_command_1(b, l + 1);
    if (!r) r = block_pipeline_command_2(b, l + 1);
    if (!r) r = trap_command(b, l + 1);
    if (!r) r = let_command(b, l + 1);
    exit_section_(b, l, m, r, false, block_pipeline_recover_parser_);
    return r;
  }

  // '!'? pipeline
  private static boolean block_pipeline_command_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_pipeline_command_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = block_pipeline_command_0_0(b, l + 1);
    r = r && pipeline(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '!'?
  private static boolean block_pipeline_command_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_pipeline_command_0_0")) return false;
    consumeToken(b, BANG);
    return true;
  }

  // timespec '!'? pipeline
  private static boolean block_pipeline_command_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_pipeline_command_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = timespec(b, l + 1);
    r = r && block_pipeline_command_1_1(b, l + 1);
    r = r && pipeline(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '!'?
  private static boolean block_pipeline_command_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_pipeline_command_1_1")) return false;
    consumeToken(b, BANG);
    return true;
  }

  // '!' timespec pipeline
  private static boolean block_pipeline_command_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_pipeline_command_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, BANG);
    r = r && timespec(b, l + 1);
    r = r && pipeline(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // block_pipeline_command (pipeline_command_list_separator block_pipeline_command)*
  static boolean block_pipeline_command_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_pipeline_command_list")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = block_pipeline_command(b, l + 1);
    p = r; // pin = 1
    r = r && block_pipeline_command_list_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (pipeline_command_list_separator block_pipeline_command)*
  private static boolean block_pipeline_command_list_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_pipeline_command_list_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!block_pipeline_command_list_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "block_pipeline_command_list_1", c)) break;
    }
    return true;
  }

  // pipeline_command_list_separator block_pipeline_command
  private static boolean block_pipeline_command_list_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_pipeline_command_list_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = pipeline_command_list_separator(b, l + 1);
    r = r && block_pipeline_command(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // !(case|if|while|until|select|'{'|function|'$'|'&&'|';'|';;'|'||'|'&'|'!'|'['|'[['|'('|')'|'|'|'|&'|'`'
  // |'\n'|'(('|time | trap | var | word|EXPR_CONDITIONAL_LEFT|ARITH_SQUARE_LEFT | do | done | '}')
  static boolean block_pipeline_recover(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_pipeline_recover")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !block_pipeline_recover_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // case|if|while|until|select|'{'|function|'$'|'&&'|';'|';;'|'||'|'&'|'!'|'['|'[['|'('|')'|'|'|'|&'|'`'
  // |'\n'|'(('|time | trap | var | word|EXPR_CONDITIONAL_LEFT|ARITH_SQUARE_LEFT | do | done | '}'
  private static boolean block_pipeline_recover_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_pipeline_recover_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
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
    if (!r) r = consumeToken(b, BACKQUOTE);
    if (!r) r = consumeToken(b, LINEFEED);
    if (!r) r = consumeToken(b, LEFT_DOUBLE_PAREN);
    if (!r) r = consumeToken(b, TIME);
    if (!r) r = consumeToken(b, TRAP);
    if (!r) r = consumeToken(b, VAR);
    if (!r) r = consumeToken(b, WORD);
    if (!r) r = consumeToken(b, EXPR_CONDITIONAL_LEFT);
    if (!r) r = consumeToken(b, ARITH_SQUARE_LEFT);
    if (!r) r = consumeToken(b, DO);
    if (!r) r = consumeToken(b, DONE);
    if (!r) r = consumeToken(b, RIGHT_CURLY);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // newlines '('? pattern ')' (compound_case_list|newlines)
  public static boolean case_clause(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_clause")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, CASE_CLAUSE, "<case clause>");
    r = newlines(b, l + 1);
    r = r && case_clause_1(b, l + 1);
    r = r && pattern(b, l + 1);
    p = r; // pin = 3
    r = r && report_error_(b, consumeToken(b, RIGHT_PAREN));
    r = p && case_clause_4(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // '('?
  private static boolean case_clause_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_clause_1")) return false;
    consumeToken(b, LEFT_PAREN);
    return true;
  }

  // compound_case_list|newlines
  private static boolean case_clause_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_clause_4")) return false;
    boolean r;
    r = compound_case_list(b, l + 1);
    if (!r) r = newlines(b, l + 1);
    return r;
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
    exit_section_(b, l, m, r, p, null);
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
  // shell_command redirection_list?
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

  // shell_command redirection_list?
  private static boolean command_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "command_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = shell_command(b, l + 1);
    r = r && command_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // redirection_list?
  private static boolean command_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "command_0_1")) return false;
    redirection_list(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // subshell_command
  static boolean command_substitution(PsiBuilder b, int l) {
    return subshell_command(b, l + 1);
  }

  /* ********************************************************** */
  // !<<isModeOn "BACKQUOTE">> '`' <<withOn "BACKQUOTE" list?>> '`'
  public static boolean command_substitution_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "command_substitution_command")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, COMMAND_SUBSTITUTION_COMMAND, "<command substitution command>");
    r = command_substitution_command_0(b, l + 1);
    r = r && consumeToken(b, BACKQUOTE);
    p = r; // pin = 2
    r = r && report_error_(b, withOn(b, l + 1, "BACKQUOTE", command_substitution_command_2_1_parser_));
    r = p && consumeToken(b, BACKQUOTE) && r;
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
  //                      '&&' newlines? pipeline_command
  //                    | '||' newlines? pipeline_command
  //                    | '&' pipeline_command?
  //                    | ';' pipeline_command?
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
  //                      '&&' newlines? pipeline_command
  //                    | '||' newlines? pipeline_command
  //                    | '&' pipeline_command?
  //                    | ';' pipeline_command?
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

  // '&&' newlines? pipeline_command
  //                    | '||' newlines? pipeline_command
  //                    | '&' pipeline_command?
  //                    | ';' pipeline_command?
  private static boolean commands_list_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commands_list_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = commands_list_1_0_0(b, l + 1);
    if (!r) r = commands_list_1_0_1(b, l + 1);
    if (!r) r = commands_list_1_0_2(b, l + 1);
    if (!r) r = commands_list_1_0_3(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '&&' newlines? pipeline_command
  private static boolean commands_list_1_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commands_list_1_0_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, AND_AND);
    p = r; // pin = 1
    r = r && report_error_(b, commands_list_1_0_0_1(b, l + 1));
    r = p && pipeline_command(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // newlines?
  private static boolean commands_list_1_0_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commands_list_1_0_0_1")) return false;
    newlines(b, l + 1);
    return true;
  }

  // '||' newlines? pipeline_command
  private static boolean commands_list_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commands_list_1_0_1")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, OR_OR);
    p = r; // pin = 1
    r = r && report_error_(b, commands_list_1_0_1_1(b, l + 1));
    r = p && pipeline_command(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // newlines?
  private static boolean commands_list_1_0_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commands_list_1_0_1_1")) return false;
    newlines(b, l + 1);
    return true;
  }

  // '&' pipeline_command?
  private static boolean commands_list_1_0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commands_list_1_0_2")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, AMP);
    p = r; // pin = 1
    r = r && commands_list_1_0_2_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // pipeline_command?
  private static boolean commands_list_1_0_2_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commands_list_1_0_2_1")) return false;
    pipeline_command(b, l + 1);
    return true;
  }

  // ';' pipeline_command?
  private static boolean commands_list_1_0_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commands_list_1_0_3")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, SEMI);
    p = r; // pin = 1
    r = r && commands_list_1_0_3_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // pipeline_command?
  private static boolean commands_list_1_0_3_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commands_list_1_0_3_1")) return false;
    pipeline_command(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // '$' &('(' | '((' | '{' | ARITH_SQUARE_LEFT) composed_var_inner
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

  // &('(' | '((' | '{' | ARITH_SQUARE_LEFT)
  private static boolean composed_var_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "composed_var_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = composed_var_1_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // '(' | '((' | '{' | ARITH_SQUARE_LEFT
  private static boolean composed_var_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "composed_var_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LEFT_PAREN);
    if (!r) r = consumeToken(b, LEFT_DOUBLE_PAREN);
    if (!r) r = consumeToken(b, LEFT_CURLY);
    if (!r) r = consumeToken(b, ARITH_SQUARE_LEFT);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // arithmetic_expansion | old_arithmetic_expansion | command_substitution | shell_parameter_expansion
  static boolean composed_var_inner(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "composed_var_inner")) return false;
    boolean r;
    r = arithmetic_expansion(b, l + 1);
    if (!r) r = old_arithmetic_expansion(b, l + 1);
    if (!r) r = command_substitution(b, l + 1);
    if (!r) r = shell_parameter_expansion(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // (nl pipeline_command_list?| pipeline_command_list) end_of_list? newlines
  public static boolean compound_case_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "compound_case_list")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, COMPOUND_LIST, "<compound case list>");
    r = compound_case_list_0(b, l + 1);
    p = r; // pin = 1
    r = r && report_error_(b, compound_case_list_1(b, l + 1));
    r = p && newlines(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // nl pipeline_command_list?| pipeline_command_list
  private static boolean compound_case_list_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "compound_case_list_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = compound_case_list_0_0(b, l + 1);
    if (!r) r = pipeline_command_list(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // nl pipeline_command_list?
  private static boolean compound_case_list_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "compound_case_list_0_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = nl(b, l + 1);
    p = r; // pin = 1
    r = r && compound_case_list_0_0_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // pipeline_command_list?
  private static boolean compound_case_list_0_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "compound_case_list_0_0_1")) return false;
    pipeline_command_list(b, l + 1);
    return true;
  }

  // end_of_list?
  private static boolean compound_case_list_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "compound_case_list_1")) return false;
    end_of_list(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // (nl pipeline_command_list | pipeline_command_list) end_of_list  newlines
  public static boolean compound_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "compound_list")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, COMPOUND_LIST, "<compound list>");
    r = compound_list_0(b, l + 1);
    p = r; // pin = 1
    r = r && report_error_(b, end_of_list(b, l + 1));
    r = p && newlines(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // nl pipeline_command_list | pipeline_command_list
  private static boolean compound_list_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "compound_list_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = compound_list_0_0(b, l + 1);
    if (!r) r = pipeline_command_list(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // nl pipeline_command_list
  private static boolean compound_list_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "compound_list_0_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = nl(b, l + 1);
    p = r; // pin = 1
    r = r && pipeline_command_list(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // '[[' condition* (']]'|']'<<differentBracketsWarning>>)
  //                         |'[' condition* (']'|']]' <<differentBracketsWarning>>)
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

  // '[[' condition* (']]'|']'<<differentBracketsWarning>>)
  private static boolean conditional_command_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "conditional_command_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, LEFT_DOUBLE_BRACKET);
    p = r; // pin = 1
    r = r && report_error_(b, conditional_command_0_1(b, l + 1));
    r = p && conditional_command_0_2(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // condition*
  private static boolean conditional_command_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "conditional_command_0_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!condition(b, l + 1, -1)) break;
      if (!empty_element_parsed_guard_(b, "conditional_command_0_1", c)) break;
    }
    return true;
  }

  // ']]'|']'<<differentBracketsWarning>>
  private static boolean conditional_command_0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "conditional_command_0_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, RIGHT_DOUBLE_BRACKET);
    if (!r) r = conditional_command_0_2_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // ']'<<differentBracketsWarning>>
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

  // '[' condition* (']'|']]' <<differentBracketsWarning>>)
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

  // condition*
  private static boolean conditional_command_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "conditional_command_1_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!condition(b, l + 1, -1)) break;
      if (!empty_element_parsed_guard_(b, "conditional_command_1_1", c)) break;
    }
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
  // do  do_block_compound_list done
  public static boolean do_block(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "do_block")) return false;
    if (!nextTokenIs(b, DO)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, DO_BLOCK, null);
    r = consumeToken(b, DO);
    p = r; // pin = 1
    r = r && report_error_(b, do_block_compound_list(b, l + 1));
    r = p && consumeToken(b, DONE) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // (nl block_pipeline_command_list|block_pipeline_command_list) end_of_list  newlines
  public static boolean do_block_compound_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "do_block_compound_list")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, COMPOUND_LIST, "<do block compound list>");
    r = do_block_compound_list_0(b, l + 1);
    p = r; // pin = 1
    r = r && report_error_(b, end_of_list(b, l + 1));
    r = p && newlines(b, l + 1) && r;
    exit_section_(b, l, m, r, p, do_block_compound_list_recover_parser_);
    return r || p;
  }

  // nl block_pipeline_command_list|block_pipeline_command_list
  private static boolean do_block_compound_list_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "do_block_compound_list_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = do_block_compound_list_0_0(b, l + 1);
    if (!r) r = block_pipeline_command_list(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // nl block_pipeline_command_list
  private static boolean do_block_compound_list_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "do_block_compound_list_0_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = nl(b, l + 1);
    p = r; // pin = 1
    r = r && block_pipeline_command_list(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // !(do | '\n' | done)
  static boolean do_block_compound_list_recover(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "do_block_compound_list_recover")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !do_block_compound_list_recover_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // do | '\n' | done
  private static boolean do_block_compound_list_recover_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "do_block_compound_list_recover_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, DO);
    if (!r) r = consumeToken(b, LINEFEED);
    if (!r) r = consumeToken(b, DONE);
    exit_section_(b, m, null, r);
    return r;
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
    Marker m = enter_section_(b);
    r = consumeToken(b, LINEFEED);
    if (!r) r = consumeToken(b, SEMI);
    if (!r) r = consumeToken(b, AMP);
    exit_section_(b, m, null, r);
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
  // arithmetic_for_clause | in_for_clause
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
  // word argument_list  newlines block
  //                        | function word argument_list? newlines block
  public static boolean function_definition(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_definition")) return false;
    if (!nextTokenIs(b, "<function definition>", FUNCTION, WORD)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FUNCTION_DEFINITION, "<function definition>");
    r = function_definition_0(b, l + 1);
    if (!r) r = function_definition_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // word argument_list  newlines block
  private static boolean function_definition_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_definition_0")) return false;
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

  // function word argument_list? newlines block
  private static boolean function_definition_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_definition_1")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeTokens(b, 1, FUNCTION, WORD);
    p = r; // pin = function|argument_list
    r = r && report_error_(b, function_definition_1_2(b, l + 1));
    r = p && report_error_(b, newlines(b, l + 1)) && r;
    r = p && block(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // argument_list?
  private static boolean function_definition_1_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_definition_1_2")) return false;
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
  // HEREDOC_MARKER_TAG HEREDOC_MARKER_START [commands_list heredoc_pipeline_separator?| heredoc_pipeline_separator commands_list?] newlines
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

  // [commands_list heredoc_pipeline_separator?| heredoc_pipeline_separator commands_list?]
  private static boolean heredoc_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "heredoc_2")) return false;
    heredoc_2_0(b, l + 1);
    return true;
  }

  // commands_list heredoc_pipeline_separator?| heredoc_pipeline_separator commands_list?
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
    Marker m = enter_section_(b);
    r = consumeToken(b, AND_AND);
    if (!r) r = consumeToken(b, OR_OR);
    if (!r) r = consumeToken(b, AMP);
    if (!r) r = consumeToken(b, PIPE);
    if (!r) r = consumeToken(b, PIPE_AMP);
    if (!r) r = consumeToken(b, SEMI);
    exit_section_(b, m, null, r);
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
  // "in" word_list list_terminator newlines
  static boolean in_clause(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "in_clause")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, "in");
    p = r; // pin = 1
    r = r && report_error_(b, word_list(b, l + 1));
    r = p && report_error_(b, list_terminator(b, l + 1)) && r;
    r = p && newlines(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
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
    Marker m = enter_section_(b);
    r = consumeToken(b, "source");
    if (!r) r = consumeToken(b, ".");
    exit_section_(b, m, null, r);
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
  // (nl pipeline_command_list | pipeline_command_list) end_of_list? newlines
  public static boolean list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "list")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, COMPOUND_LIST, "<list>");
    r = list_0(b, l + 1);
    p = r; // pin = 1
    r = r && report_error_(b, list_1(b, l + 1));
    r = p && newlines(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // nl pipeline_command_list | pipeline_command_list
  private static boolean list_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "list_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = list_0_0(b, l + 1);
    if (!r) r = pipeline_command_list(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // nl pipeline_command_list
  private static boolean list_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "list_0_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = nl(b, l + 1);
    p = r; // pin = 1
    r = r && pipeline_command_list(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // end_of_list?
  private static boolean list_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "list_1")) return false;
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
    exit_section_(b, l, m, r, false, any_block_recover_parser_);
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
  // '!' | vars | '$' | bash_expansion | 'file descriptor'
  static boolean not_lvalue(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "not_lvalue")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, BANG);
    if (!r) r = vars(b, l + 1);
    if (!r) r = consumeToken(b, DOLLAR);
    if (!r) r = bash_expansion(b, l + 1);
    if (!r) r = consumeToken(b, FILEDESCRIPTOR);
    exit_section_(b, m, null, r);
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
    exit_section_(b, l, m, r, false, old_arithmetic_expansion_expression_recover_parser_);
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
  // w+ ('|' w+)*
  public static boolean pattern(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, PATTERN, "<pattern>");
    r = pattern_0(b, l + 1);
    p = r; // pin = 1
    r = r && pattern_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // w+
  private static boolean pattern_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = w(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!w(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "pattern_0", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // ('|' w+)*
  private static boolean pattern_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!pattern_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "pattern_1", c)) break;
    }
    return true;
  }

  // '|' w+
  private static boolean pattern_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern_1_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, PIPE);
    p = r; // pin = 1
    r = r && pattern_1_0_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // w+
  private static boolean pattern_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern_1_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = w(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!w(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "pattern_1_0_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // command (('|&'|'|') newlines command)*
  public static boolean pipeline(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, PIPELINE, "<pipeline>");
    r = command(b, l + 1);
    p = r; // pin = 1
    r = r && pipeline_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (('|&'|'|') newlines command)*
  private static boolean pipeline_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!pipeline_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "pipeline_1", c)) break;
    }
    return true;
  }

  // ('|&'|'|') newlines command
  private static boolean pipeline_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_1_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = pipeline_1_0_0(b, l + 1);
    p = r; // pin = 1
    r = r && report_error_(b, newlines(b, l + 1));
    r = p && command(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // '|&'|'|'
  private static boolean pipeline_1_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_1_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, PIPE_AMP);
    if (!r) r = consumeToken(b, PIPE);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '!'? pipeline
  //                     | timespec '!'? pipeline
  //                     | '!' timespec pipeline
  //                     | trap_command
  //                     | let_command
  public static boolean pipeline_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_command")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, PIPELINE_COMMAND, "<pipeline command>");
    r = pipeline_command_0(b, l + 1);
    if (!r) r = pipeline_command_1(b, l + 1);
    if (!r) r = pipeline_command_2(b, l + 1);
    if (!r) r = trap_command(b, l + 1);
    if (!r) r = let_command(b, l + 1);
    exit_section_(b, l, m, r, false, pipeline_recover_parser_);
    return r;
  }

  // '!'? pipeline
  private static boolean pipeline_command_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_command_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = pipeline_command_0_0(b, l + 1);
    r = r && pipeline(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '!'?
  private static boolean pipeline_command_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_command_0_0")) return false;
    consumeToken(b, BANG);
    return true;
  }

  // timespec '!'? pipeline
  private static boolean pipeline_command_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_command_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = timespec(b, l + 1);
    r = r && pipeline_command_1_1(b, l + 1);
    r = r && pipeline(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '!'?
  private static boolean pipeline_command_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_command_1_1")) return false;
    consumeToken(b, BANG);
    return true;
  }

  // '!' timespec pipeline
  private static boolean pipeline_command_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_command_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, BANG);
    r = r && timespec(b, l + 1);
    r = r && pipeline(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
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
  // ('&&'|  '||' |  '&' |  ';' |  '\n') newlines
  static boolean pipeline_command_list_separator(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_command_list_separator")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = pipeline_command_list_separator_0(b, l + 1);
    r = r && newlines(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '&&'|  '||' |  '&' |  ';' |  '\n'
  private static boolean pipeline_command_list_separator_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_command_list_separator_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, AND_AND);
    if (!r) r = consumeToken(b, OR_OR);
    if (!r) r = consumeToken(b, AMP);
    if (!r) r = consumeToken(b, SEMI);
    if (!r) r = consumeToken(b, LINEFEED);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // !(case|if|while|until|select|'{'|function|'$'|'&&'|';'|';;'|'||'|'&'|'!'|'['|'[['|'('|')'|'|'|'|&'|'`'
  // |'\n'|'(('|time | trap | var | word|EXPR_CONDITIONAL_LEFT|ARITH_SQUARE_LEFT | do)
  static boolean pipeline_recover(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_recover")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !pipeline_recover_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // case|if|while|until|select|'{'|function|'$'|'&&'|';'|';;'|'||'|'&'|'!'|'['|'[['|'('|')'|'|'|'|&'|'`'
  // |'\n'|'(('|time | trap | var | word|EXPR_CONDITIONAL_LEFT|ARITH_SQUARE_LEFT | do
  private static boolean pipeline_recover_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_recover_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
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
    if (!r) r = consumeToken(b, BACKQUOTE);
    if (!r) r = consumeToken(b, LINEFEED);
    if (!r) r = consumeToken(b, LEFT_DOUBLE_PAREN);
    if (!r) r = consumeToken(b, TIME);
    if (!r) r = consumeToken(b, TRAP);
    if (!r) r = consumeToken(b, VAR);
    if (!r) r = consumeToken(b, WORD);
    if (!r) r = consumeToken(b, EXPR_CONDITIONAL_LEFT);
    if (!r) r = consumeToken(b, ARITH_SQUARE_LEFT);
    if (!r) r = consumeToken(b, DO);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // ('<' '<'?| '>') '(' list ')'
  public static boolean process_substitution(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "process_substitution")) return false;
    if (!nextTokenIs(b, "<process substitution>", GT, LT)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, PROCESS_SUBSTITUTION, "<process substitution>");
    r = process_substitution_0(b, l + 1);
    p = r; // pin = 1
    r = r && report_error_(b, consumeToken(b, LEFT_PAREN));
    r = p && report_error_(b, list(b, l + 1)) && r;
    r = p && consumeToken(b, RIGHT_PAREN) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // '<' '<'?| '>'
  private static boolean process_substitution_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "process_substitution_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = process_substitution_0_0(b, l + 1);
    if (!r) r = consumeToken(b, GT);
    exit_section_(b, m, null, r);
    return r;
  }

  // '<' '<'?
  private static boolean process_substitution_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "process_substitution_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LT);
    r = r && process_substitution_0_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '<'?
  private static boolean process_substitution_0_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "process_substitution_0_0_1")) return false;
    consumeToken(b, LT);
    return true;
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
  //                             | ('>' | '<' | '>>' | '<<<' | '<<' | '<&' | '>&' | '&>>' | '<>' | '>|') w+
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
    Marker m = enter_section_(b);
    r = consumeToken(b, REDIRECT_LESS_AMP);
    if (!r) r = consumeToken(b, REDIRECT_GREATER_AMP);
    exit_section_(b, m, null, r);
    return r;
  }

  // number | '-'
  private static boolean redirection_inner_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_inner_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = number(b, l + 1);
    if (!r) r = consumeToken(b, MINUS);
    exit_section_(b, m, null, r);
    return r;
  }

  // ('>' | '<' | '>>' | '<<<' | '<<' | '<&' | '>&' | '&>>' | '<>' | '>|') w+
  private static boolean redirection_inner_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_inner_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = redirection_inner_1_0(b, l + 1);
    r = r && redirection_inner_1_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '>' | '<' | '>>' | '<<<' | '<<' | '<&' | '>&' | '&>>' | '<>' | '>|'
  private static boolean redirection_inner_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_inner_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
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
    exit_section_(b, m, null, r);
    return r;
  }

  // w+
  private static boolean redirection_inner_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_inner_1_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = w(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!w(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "redirection_inner_1_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // redirection redirection*
  public static boolean redirection_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_list")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, REDIRECTION_LIST, "<redirection list>");
    r = redirection(b, l + 1);
    p = r; // pin = 1
    r = r && redirection_list_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // redirection*
  private static boolean redirection_list_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_list_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!redirection(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "redirection_list_1", c)) break;
    }
    return true;
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
  // for_command
  //                   | case_command
  //                   | while_command
  //                   | until_command
  //                   | select_command
  //                   | if_command
  //                   | subshell_command
  //                   | block
  //                   | function_definition
  public static boolean shell_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "shell_command")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, SHELL_COMMAND, "<shell command>");
    r = for_command(b, l + 1);
    if (!r) r = case_command(b, l + 1);
    if (!r) r = while_command(b, l + 1);
    if (!r) r = until_command(b, l + 1);
    if (!r) r = select_command(b, l + 1);
    if (!r) r = if_command(b, l + 1);
    if (!r) r = subshell_command(b, l + 1);
    if (!r) r = block(b, l + 1);
    if (!r) r = function_definition(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // '{' parameter_expansion_body (composed_var parameter_expansion_body?)* '}'
  public static boolean shell_parameter_expansion(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "shell_parameter_expansion")) return false;
    if (!nextTokenIs(b, LEFT_CURLY)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, SHELL_PARAMETER_EXPANSION, null);
    r = consumeTokens(b, 1, LEFT_CURLY, PARAMETER_EXPANSION_BODY);
    p = r; // pin = 1
    r = r && report_error_(b, shell_parameter_expansion_2(b, l + 1));
    r = p && consumeToken(b, RIGHT_CURLY) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (composed_var parameter_expansion_body?)*
  private static boolean shell_parameter_expansion_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "shell_parameter_expansion_2")) return false;
    while (true) {
      int c = current_position_(b);
      if (!shell_parameter_expansion_2_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "shell_parameter_expansion_2", c)) break;
    }
    return true;
  }

  // composed_var parameter_expansion_body?
  private static boolean shell_parameter_expansion_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "shell_parameter_expansion_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = composed_var(b, l + 1);
    r = r && shell_parameter_expansion_2_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // parameter_expansion_body?
  private static boolean shell_parameter_expansion_2_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "shell_parameter_expansion_2_0_1")) return false;
    consumeToken(b, PARAMETER_EXPANSION_BODY);
    return true;
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
  // (commands_list ['&' | ';' | newlines])*
  static boolean simple_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simple_list")) return false;
    while (true) {
      int c = current_position_(b);
      if (!simple_list_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "simple_list", c)) break;
    }
    return true;
  }

  // commands_list ['&' | ';' | newlines]
  private static boolean simple_list_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simple_list_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = commands_list(b, l + 1);
    p = r; // pin = 1
    r = r && simple_list_0_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // ['&' | ';' | newlines]
  private static boolean simple_list_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simple_list_0_1")) return false;
    simple_list_0_1_0(b, l + 1);
    return true;
  }

  // '&' | ';' | newlines
  private static boolean simple_list_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simple_list_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, AMP);
    if (!r) r = consumeToken(b, SEMI);
    if (!r) r = newlines(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // ('"' (word | vars | <<notQuote>>)* '"') | RAW_STRING
  public static boolean string(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "string")) return false;
    if (!nextTokenIs(b, "<string>", QUOTE, RAW_STRING)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, STRING, "<string>");
    r = string_0(b, l + 1);
    if (!r) r = consumeToken(b, RAW_STRING);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // '"' (word | vars | <<notQuote>>)* '"'
  private static boolean string_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "string_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, QUOTE);
    p = r; // pin = 1
    r = r && report_error_(b, string_0_1(b, l + 1));
    r = p && consumeToken(b, QUOTE) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (word | vars | <<notQuote>>)*
  private static boolean string_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "string_0_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!string_0_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "string_0_1", c)) break;
    }
    return true;
  }

  // word | vars | <<notQuote>>
  private static boolean string_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "string_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, WORD);
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
  // '-p'
  public static boolean time_opt(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "time_opt")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, TIME_OPT, "<time opt>");
    r = consumeToken(b, "-p");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // time time_opt?
  public static boolean timespec(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "timespec")) return false;
    if (!nextTokenIs(b, TIME)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, TIME);
    r = r && timespec_1(b, l + 1);
    exit_section_(b, m, TIMESPEC, r);
    return r;
  }

  // time_opt?
  private static boolean timespec_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "timespec_1")) return false;
    time_opt(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // trap literal*
  public static boolean trap_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "trap_command")) return false;
    if (!nextTokenIs(b, TRAP)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, TRAP_COMMAND, null);
    r = consumeToken(b, TRAP);
    p = r; // pin = 1
    r = r && trap_command_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // literal*
  private static boolean trap_command_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "trap_command_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!literal(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "trap_command_1", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // until do_block_compound_list  do_block
  public static boolean until_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "until_command")) return false;
    if (!nextTokenIs(b, UNTIL)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, UNTIL_COMMAND, null);
    r = consumeToken(b, UNTIL);
    p = r; // pin = 1
    r = r && report_error_(b, do_block_compound_list(b, l + 1));
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
  // variable | composed_var | command_substitution_command
  static boolean vars(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "vars")) return false;
    boolean r;
    r = variable(b, l + 1);
    if (!r) r = composed_var(b, l + 1);
    if (!r) r = command_substitution_command(b, l + 1);
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
  // while do_block_compound_list  do_block
  public static boolean while_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "while_command")) return false;
    if (!nextTokenIs(b, WHILE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, WHILE_COMMAND, null);
    r = consumeToken(b, WHILE);
    p = r; // pin = 1
    r = r && report_error_(b, do_block_compound_list(b, l + 1));
    r = p && do_block(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // w+
  static boolean word_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "word_list")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = w(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!w(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "word_list", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // Expression root: condition
  // Operator priority table:
  // 0: BINARY(assignment_condition)
  // 1: BINARY(logical_or_condition)
  // 2: BINARY(logical_and_condition)
  // 3: BINARY(equality_condition)
  // 4: BINARY(comparison_condition)
  // 5: PREFIX(logical_bitwise_condition)
  // 6: ATOM(literal_condition)
  // 7: PREFIX(parentheses_condition)
  public static boolean condition(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "condition")) return false;
    addVariant(b, "<condition>");
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, "<condition>");
    r = logical_bitwise_condition(b, l + 1);
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
      if (g < 0 && consumeTokenSmart(b, ASSIGN)) {
        r = condition(b, l, 0);
        exit_section_(b, l, m, ASSIGNMENT_CONDITION, r, true, null);
      }
      else if (g < 1 && consumeTokenSmart(b, OR_OR)) {
        r = condition(b, l, 1);
        exit_section_(b, l, m, LOGICAL_OR_CONDITION, r, true, null);
      }
      else if (g < 2 && consumeTokenSmart(b, AND_AND)) {
        r = condition(b, l, 2);
        exit_section_(b, l, m, LOGICAL_AND_CONDITION, r, true, null);
      }
      else if (g < 3 && equality_condition_0(b, l + 1)) {
        r = condition(b, l, 3);
        exit_section_(b, l, m, EQUALITY_CONDITION, r, true, null);
      }
      else if (g < 4 && comparison_condition_0(b, l + 1)) {
        r = condition(b, l, 4);
        exit_section_(b, l, m, COMPARISON_CONDITION, r, true, null);
      }
      else {
        exit_section_(b, l, m, null, false, false, null);
        break;
      }
    }
    return r;
  }

  // '==' | '!=' | '=~'
  private static boolean equality_condition_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "equality_condition_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, EQ);
    if (!r) r = consumeTokenSmart(b, NE);
    if (!r) r = consumeTokenSmart(b, REGEXP);
    exit_section_(b, m, null, r);
    return r;
  }

  // '<' | '>'
  private static boolean comparison_condition_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "comparison_condition_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, LT);
    if (!r) r = consumeTokenSmart(b, GT);
    exit_section_(b, m, null, r);
    return r;
  }

  public static boolean logical_bitwise_condition(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "logical_bitwise_condition")) return false;
    if (!nextTokenIsSmart(b, BANG)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = consumeTokenSmart(b, BANG);
    p = r;
    r = p && condition(b, l, 5);
    exit_section_(b, l, m, LOGICAL_BITWISE_CONDITION, r, p, null);
    return r || p;
  }

  // w newlines | newlines w
  public static boolean literal_condition(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "literal_condition")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, LITERAL_CONDITION, "<literal condition>");
    r = literal_condition_0(b, l + 1);
    if (!r) r = literal_condition_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // w newlines
  private static boolean literal_condition_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "literal_condition_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = w(b, l + 1);
    r = r && newlines(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // newlines w
  private static boolean literal_condition_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "literal_condition_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = newlines(b, l + 1);
    r = r && w(b, l + 1);
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
    Marker m = enter_section_(b);
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
    exit_section_(b, m, null, r);
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
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, EQ);
    if (!r) r = consumeTokenSmart(b, NE);
    exit_section_(b, m, null, r);
    return r;
  }

  // '<=' | '>=' | '<' | '>'
  private static boolean comparison_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "comparison_expression_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, LE);
    if (!r) r = consumeTokenSmart(b, GE);
    if (!r) r = consumeTokenSmart(b, LT);
    if (!r) r = consumeTokenSmart(b, GT);
    exit_section_(b, m, null, r);
    return r;
  }

  // '<<' | '>>'
  private static boolean bitwise_shift_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bitwise_shift_expression_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, SHIFT_LEFT);
    if (!r) r = consumeTokenSmart(b, SHIFT_RIGHT);
    exit_section_(b, m, null, r);
    return r;
  }

  // '+' | '-'
  private static boolean add_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "add_expression_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, PLUS);
    if (!r) r = consumeTokenSmart(b, MINUS);
    exit_section_(b, m, null, r);
    return r;
  }

  // '*' | '/' | '%'
  private static boolean mul_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "mul_expression_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, MULT);
    if (!r) r = consumeTokenSmart(b, DIV);
    if (!r) r = consumeTokenSmart(b, MOD);
    exit_section_(b, m, null, r);
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
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, BANG);
    if (!r) r = consumeTokenSmart(b, BITWISE_NEGATION);
    exit_section_(b, m, null, r);
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
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, MINUS);
    if (!r) r = consumeTokenSmart(b, PLUS);
    exit_section_(b, m, null, r);
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
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, MINUS_MINUS);
    if (!r) r = consumeTokenSmart(b, PLUS_PLUS);
    exit_section_(b, m, null, r);
    return r;
  }

  // '--' | '++'
  private static boolean post_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "post_expression_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, MINUS_MINUS);
    if (!r) r = consumeTokenSmart(b, PLUS_PLUS);
    exit_section_(b, m, null, r);
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

  static final Parser any_block_recover_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return any_block_recover(b, l + 1);
    }
  };
  static final Parser argument_list_recover_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return argument_list_recover(b, l + 1);
    }
  };
  static final Parser arithmetic_for_expression_recover_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return arithmetic_for_expression_recover(b, l + 1);
    }
  };
  static final Parser assignment_command_2_0_1_0_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return assignment_command_2_0_1_0(b, l + 1);
    }
  };
  static final Parser block_compound_list_recover_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return block_compound_list_recover(b, l + 1);
    }
  };
  static final Parser block_pipeline_recover_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return block_pipeline_recover(b, l + 1);
    }
  };
  static final Parser command_substitution_command_2_1_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return command_substitution_command_2_1(b, l + 1);
    }
  };
  static final Parser do_block_compound_list_recover_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return do_block_compound_list_recover(b, l + 1);
    }
  };
  static final Parser literal_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return literal(b, l + 1);
    }
  };
  static final Parser old_arithmetic_expansion_expression_recover_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return old_arithmetic_expansion_expression_recover(b, l + 1);
    }
  };
  static final Parser pipeline_recover_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return pipeline_recover(b, l + 1);
    }
  };
}
