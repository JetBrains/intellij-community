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
    else if (t == ASSIGNMENT_WORD_RULE) {
      r = assignment_word_rule(b, 0);
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
    else if (t == COMMANDS_LIST) {
      r = commands_list(b, 0);
    }
    else if (t == COMPOUND_LIST) {
      r = compound_list(b, 0);
    }
    else if (t == CONDITIONAL) {
      r = conditional(b, 0);
    }
    else if (t == DO_BLOCK) {
      r = do_block(b, 0);
    }
    else if (t == ELIF_CLAUSE) {
      r = elif_clause(b, 0);
    }
    else if (t == EXPRESSION) {
      r = expression(b, 0, -1);
    }
    else if (t == FOR_COMMAND) {
      r = for_command(b, 0);
    }
    else if (t == FUNCTION_DEF) {
      r = function_def(b, 0);
    }
    else if (t == GROUP_COMMAND) {
      r = group_command(b, 0);
    }
    else if (t == HEREDOC) {
      r = heredoc(b, 0);
    }
    else if (t == IF_COMMAND) {
      r = if_command(b, 0);
    }
    else if (t == LIST_TERMINATOR) {
      r = list_terminator(b, 0);
    }
    else if (t == PATTERN) {
      r = pattern(b, 0);
    }
    else if (t == PATTERN_LIST) {
      r = pattern_list(b, 0);
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
    else if (t == SUBSHELL) {
      r = subshell(b, 0);
    }
    else if (t == TIME_OPT) {
      r = time_opt(b, 0);
    }
    else if (t == TIMESPEC) {
      r = timespec(b, 0);
    }
    else if (t == UNTIL_COMMAND) {
      r = until_command(b, 0);
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
    create_token_set_(BLOCK, DO_BLOCK),
    create_token_set_(CASE_COMMAND, COMMAND, FOR_COMMAND, GROUP_COMMAND,
      IF_COMMAND, PIPELINE_COMMAND, SELECT_COMMAND, SHELL_COMMAND,
      SIMPLE_COMMAND, UNTIL_COMMAND, WHILE_COMMAND),
    create_token_set_(ADD_EXPRESSION, ASSIGNMENT_EXPRESSION, BITWISE_AND_EXPRESSION, BITWISE_EXCLUSIVE_OR_EXPRESSION,
      BITWISE_OR_EXPRESSION, BITWISE_SHIFT_EXPRESSION, COMMA_EXPRESSION, COMPARISON_EXPRESSION,
      CONDITIONAL_EXPRESSION, EQUALITY_EXPRESSION, EXPRESSION, EXP_EXPRESSION,
      LITERAL_EXPRESSION, LOGICAL_AND_EXPRESSION, LOGICAL_BITWISE_NEGATION_EXPRESSION, LOGICAL_OR_EXPRESSION,
      MUL_EXPRESSION, PARENTHESES_EXPRESSION, POST_EXPRESSION, PRE_EXPRESSION,
      UNARY_EXPRESSION),
  };

  /* ********************************************************** */
  // '((' expression '))'
  public static boolean arithmetic_expansion(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arithmetic_expansion")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, ARITHMETIC_EXPANSION, "<arithmetic expansion>");
    r = consumeToken(b, "((");
    p = r; // pin = 1
    r = r && report_error_(b, expression(b, l + 1, -1));
    r = p && consumeToken(b, "))") && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // '((' expression ';' expression ';' expression '))' [list_terminator newlines] block
  static boolean arithmetic_for(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arithmetic_for")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, "((");
    p = r; // pin = 1
    r = r && report_error_(b, expression(b, l + 1, -1));
    r = p && report_error_(b, consumeToken(b, SEMI)) && r;
    r = p && report_error_(b, expression(b, l + 1, -1)) && r;
    r = p && report_error_(b, consumeToken(b, SEMI)) && r;
    r = p && report_error_(b, expression(b, l + 1, -1)) && r;
    r = p && report_error_(b, consumeToken(b, "))")) && r;
    r = p && report_error_(b, arithmetic_for_7(b, l + 1)) && r;
    r = p && block(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // [list_terminator newlines]
  private static boolean arithmetic_for_7(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arithmetic_for_7")) return false;
    arithmetic_for_7_0(b, l + 1);
    return true;
  }

  // list_terminator newlines
  private static boolean arithmetic_for_7_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arithmetic_for_7_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = list_terminator(b, l + 1);
    r = r && newlines(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // (assignment_word | word) '=' [literal | composed_var]
  public static boolean assignment_word_rule(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "assignment_word_rule")) return false;
    if (!nextTokenIs(b, "<assignment word rule>", ASSIGNMENT_WORD, WORD)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ASSIGNMENT_WORD_RULE, "<assignment word rule>");
    r = assignment_word_rule_0(b, l + 1);
    r = r && consumeToken(b, EQ);
    r = r && assignment_word_rule_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // assignment_word | word
  private static boolean assignment_word_rule_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "assignment_word_rule_0")) return false;
    boolean r;
    r = consumeToken(b, ASSIGNMENT_WORD);
    if (!r) r = consumeToken(b, WORD);
    return r;
  }

  // [literal | composed_var]
  private static boolean assignment_word_rule_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "assignment_word_rule_2")) return false;
    assignment_word_rule_2_0(b, l + 1);
    return true;
  }

  // literal | composed_var
  private static boolean assignment_word_rule_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "assignment_word_rule_2_0")) return false;
    boolean r;
    r = literal(b, l + 1);
    if (!r) r = composed_var(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  public static boolean bash_expansion(PsiBuilder b, int l) {
    Marker m = enter_section_(b);
    exit_section_(b, m, BASH_EXPANSION, true);
    return true;
  }

  /* ********************************************************** */
  // '{' compound_list '}' | do_block
  public static boolean block(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block")) return false;
    if (!nextTokenIs(b, "<block>", DO, LEFT_CURLY)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, BLOCK, "<block>");
    r = block_0(b, l + 1);
    if (!r) r = do_block(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // '{' compound_list '}'
  private static boolean block_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "block_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, LEFT_CURLY);
    p = r; // pin = 1
    r = r && report_error_(b, compound_list(b, l + 1));
    r = p && consumeToken(b, RIGHT_CURLY) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // pattern_list
  public static boolean case_clause(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_clause")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, CASE_CLAUSE, "<case clause>");
    r = pattern_list(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // (case_clause ';;')+
  static boolean case_clause_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_clause_list")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = case_clause_list_0(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!case_clause_list_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "case_clause_list", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // case_clause ';;'
  private static boolean case_clause_list_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_clause_list_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = case_clause(b, l + 1);
    p = r; // pin = 1
    r = r && consumeToken(b, CASE_END);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // case w newlines "in" (case_clause_list newlines) esac
  public static boolean case_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_command")) return false;
    if (!nextTokenIs(b, CASE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, CASE_COMMAND, null);
    r = consumeToken(b, CASE);
    p = r; // pin = 1
    r = r && report_error_(b, w(b, l + 1));
    r = p && report_error_(b, newlines(b, l + 1)) && r;
    r = p && report_error_(b, consumeToken(b, "in")) && r;
    r = p && report_error_(b, case_command_4(b, l + 1)) && r;
    r = p && consumeToken(b, ESAC) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // case_clause_list newlines
  private static boolean case_command_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_command_4")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = case_clause_list(b, l + 1);
    p = r; // pin = 1
    r = r && newlines(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // simple_command | shell_command redirection_list?
  public static boolean command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "command")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, COMMAND, "<command>");
    r = simple_command(b, l + 1);
    if (!r) r = command_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // shell_command redirection_list?
  private static boolean command_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "command_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = shell_command(b, l + 1);
    r = r && command_1_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // redirection_list?
  private static boolean command_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "command_1_1")) return false;
    redirection_list(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // subshell
  static boolean command_substitution(PsiBuilder b, int l) {
    return subshell(b, l + 1);
  }

  /* ********************************************************** */
  // pipeline_command (
  //                      '&&' newlines pipeline_command
  //                    | '||' newlines pipeline_command
  //                    | '&' pipeline_command
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
  //                      '&&' newlines pipeline_command
  //                    | '||' newlines pipeline_command
  //                    | '&' pipeline_command
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

  // '&&' newlines pipeline_command
  //                    | '||' newlines pipeline_command
  //                    | '&' pipeline_command
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

  // '&&' newlines pipeline_command
  private static boolean commands_list_1_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commands_list_1_0_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, AND_AND);
    p = r; // pin = 1
    r = r && report_error_(b, newlines(b, l + 1));
    r = p && pipeline_command(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // '||' newlines pipeline_command
  private static boolean commands_list_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commands_list_1_0_1")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, OR_OR);
    p = r; // pin = 1
    r = r && report_error_(b, newlines(b, l + 1));
    r = p && pipeline_command(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // '&' pipeline_command
  private static boolean commands_list_1_0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commands_list_1_0_2")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, AMP);
    p = r; // pin = 1
    r = r && pipeline_command(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
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
  // '$' (arithmetic_expansion|command_substitution|shell_parameter_expansion)
  static boolean composed_var(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "composed_var")) return false;
    if (!nextTokenIs(b, DOLLAR)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, DOLLAR);
    r = r && composed_var_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // arithmetic_expansion|command_substitution|shell_parameter_expansion
  private static boolean composed_var_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "composed_var_1")) return false;
    boolean r;
    r = arithmetic_expansion(b, l + 1);
    if (!r) r = command_substitution(b, l + 1);
    if (!r) r = shell_parameter_expansion(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // newlines pipeline_command_list ('\n' | '&' | ';') newlines
  public static boolean compound_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "compound_list")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, COMPOUND_LIST, "<compound list>");
    r = newlines(b, l + 1);
    r = r && pipeline_command_list(b, l + 1);
    p = r; // pin = 2
    r = r && report_error_(b, compound_list_2(b, l + 1));
    r = p && newlines(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // '\n' | '&' | ';'
  private static boolean compound_list_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "compound_list_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LINEFEED);
    if (!r) r = consumeToken(b, AMP);
    if (!r) r = consumeToken(b, SEMI);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // EXPR_CONDITIONAL_LEFT | '[['
  static boolean cond_left(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "cond_left")) return false;
    if (!nextTokenIs(b, "", EXPR_CONDITIONAL_LEFT, LEFT_DOUBLE_BRACKET)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, EXPR_CONDITIONAL_LEFT);
    if (!r) r = consumeToken(b, LEFT_DOUBLE_BRACKET);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // EXPR_CONDITIONAL_RIGHT | ']]'
  static boolean cond_right(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "cond_right")) return false;
    if (!nextTokenIs(b, "", EXPR_CONDITIONAL_RIGHT, RIGHT_DOUBLE_BRACKET)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, EXPR_CONDITIONAL_RIGHT);
    if (!r) r = consumeToken(b, RIGHT_DOUBLE_BRACKET);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // cond_left [literal? <<condOp>> literal] cond_right
  public static boolean conditional(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "conditional")) return false;
    if (!nextTokenIs(b, "<conditional>", EXPR_CONDITIONAL_LEFT, LEFT_DOUBLE_BRACKET)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, CONDITIONAL, "<conditional>");
    r = cond_left(b, l + 1);
    p = r; // pin = 1
    r = r && report_error_(b, conditional_1(b, l + 1));
    r = p && cond_right(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // [literal? <<condOp>> literal]
  private static boolean conditional_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "conditional_1")) return false;
    conditional_1_0(b, l + 1);
    return true;
  }

  // literal? <<condOp>> literal
  private static boolean conditional_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "conditional_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = conditional_1_0_0(b, l + 1);
    r = r && condOp(b, l + 1);
    r = r && literal(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // literal?
  private static boolean conditional_1_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "conditional_1_0_0")) return false;
    literal(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // do compound_list done
  public static boolean do_block(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "do_block")) return false;
    if (!nextTokenIs(b, DO)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, DO_BLOCK, null);
    r = consumeToken(b, DO);
    p = r; // pin = 1
    r = r && report_error_(b, compound_list(b, l + 1));
    r = p && consumeToken(b, DONE) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // elif compound_list then compound_list |
  //                 elif compound_list then compound_list else compound_list |
  //                 elif compound_list then compound_list elif_clause
  public static boolean elif_clause(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "elif_clause")) return false;
    if (!nextTokenIs(b, ELIF)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = elif_clause_0(b, l + 1);
    if (!r) r = elif_clause_1(b, l + 1);
    if (!r) r = elif_clause_2(b, l + 1);
    exit_section_(b, m, ELIF_CLAUSE, r);
    return r;
  }

  // elif compound_list then compound_list
  private static boolean elif_clause_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "elif_clause_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, ELIF);
    p = r; // pin = elif|then|else
    r = r && report_error_(b, compound_list(b, l + 1));
    r = p && report_error_(b, consumeToken(b, THEN)) && r;
    r = p && compound_list(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // elif compound_list then compound_list else compound_list
  private static boolean elif_clause_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "elif_clause_1")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, ELIF);
    p = r; // pin = elif|then|else
    r = r && report_error_(b, compound_list(b, l + 1));
    r = p && report_error_(b, consumeToken(b, THEN)) && r;
    r = p && report_error_(b, compound_list(b, l + 1)) && r;
    r = p && report_error_(b, consumeToken(b, ELSE)) && r;
    r = p && compound_list(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // elif compound_list then compound_list elif_clause
  private static boolean elif_clause_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "elif_clause_2")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, ELIF);
    p = r; // pin = elif|then|else
    r = r && report_error_(b, compound_list(b, l + 1));
    r = p && report_error_(b, consumeToken(b, THEN)) && r;
    r = p && report_error_(b, compound_list(b, l + 1)) && r;
    r = p && elif_clause(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
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
  // for (
  //                     w (newlines in_clause? block | ';' newlines block)
  //                   | arithmetic_for
  //                   )
  public static boolean for_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_command")) return false;
    if (!nextTokenIs(b, FOR)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, FOR_COMMAND, null);
    r = consumeToken(b, FOR);
    p = r; // pin = 1
    r = r && for_command_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // w (newlines in_clause? block | ';' newlines block)
  //                   | arithmetic_for
  private static boolean for_command_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_command_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = for_command_1_0(b, l + 1);
    if (!r) r = arithmetic_for(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // w (newlines in_clause? block | ';' newlines block)
  private static boolean for_command_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_command_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = w(b, l + 1);
    r = r && for_command_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // newlines in_clause? block | ';' newlines block
  private static boolean for_command_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_command_1_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = for_command_1_0_1_0(b, l + 1);
    if (!r) r = for_command_1_0_1_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // newlines in_clause? block
  private static boolean for_command_1_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_command_1_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = newlines(b, l + 1);
    r = r && for_command_1_0_1_0_1(b, l + 1);
    r = r && block(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // in_clause?
  private static boolean for_command_1_0_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_command_1_0_1_0_1")) return false;
    in_clause(b, l + 1);
    return true;
  }

  // ';' newlines block
  private static boolean for_command_1_0_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_command_1_0_1_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SEMI);
    r = r && newlines(b, l + 1);
    r = r && block(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // w '(' ')' newlines group_command
  //                  |  function w '(' ')' newlines group_command
  //                  |  function w newlines group_command
  public static boolean function_def(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_def")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FUNCTION_DEF, "<function def>");
    r = function_def_0(b, l + 1);
    if (!r) r = function_def_1(b, l + 1);
    if (!r) r = function_def_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // w '(' ')' newlines group_command
  private static boolean function_def_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_def_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = w(b, l + 1);
    r = r && consumeTokens(b, 0, LEFT_PAREN, RIGHT_PAREN);
    r = r && newlines(b, l + 1);
    r = r && group_command(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // function w '(' ')' newlines group_command
  private static boolean function_def_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_def_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, FUNCTION);
    r = r && w(b, l + 1);
    r = r && consumeTokens(b, 0, LEFT_PAREN, RIGHT_PAREN);
    r = r && newlines(b, l + 1);
    r = r && group_command(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // function w newlines group_command
  private static boolean function_def_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_def_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, FUNCTION);
    r = r && w(b, l + 1);
    r = r && newlines(b, l + 1);
    r = r && group_command(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '{' compound_list '}'
  public static boolean group_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "group_command")) return false;
    if (!nextTokenIs(b, LEFT_CURLY)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, GROUP_COMMAND, null);
    r = consumeToken(b, LEFT_CURLY);
    p = r; // pin = 1
    r = r && report_error_(b, compound_list(b, l + 1));
    r = p && consumeToken(b, RIGHT_CURLY) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // HEREDOC_MARKER_TAG HEREDOC_MARKER_START newlines HEREDOC_CONTENT HEREDOC_MARKER_END
  public static boolean heredoc(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "heredoc")) return false;
    if (!nextTokenIs(b, HEREDOC_MARKER_TAG)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, HEREDOC_MARKER_TAG, HEREDOC_MARKER_START);
    r = r && newlines(b, l + 1);
    r = r && consumeTokens(b, 0, HEREDOC_CONTENT, HEREDOC_MARKER_END);
    exit_section_(b, m, HEREDOC, r);
    return r;
  }

  /* ********************************************************** */
  // if conditional list_terminator then compound_list
  //                (   fi
  //                  | else compound_list fi
  //                  | elif_clause fi)
  public static boolean if_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "if_command")) return false;
    if (!nextTokenIs(b, IF)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, IF_COMMAND, null);
    r = consumeToken(b, IF);
    p = r; // pin = if|elif|then|else
    r = r && report_error_(b, conditional(b, l + 1));
    r = p && report_error_(b, list_terminator(b, l + 1)) && r;
    r = p && report_error_(b, consumeToken(b, THEN)) && r;
    r = p && report_error_(b, compound_list(b, l + 1)) && r;
    r = p && if_command_5(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // fi
  //                  | else compound_list fi
  //                  | elif_clause fi
  private static boolean if_command_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "if_command_5")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, FI);
    if (!r) r = if_command_5_1(b, l + 1);
    if (!r) r = if_command_5_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // else compound_list fi
  private static boolean if_command_5_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "if_command_5_1")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, ELSE);
    p = r; // pin = if|elif|then|else
    r = r && report_error_(b, compound_list(b, l + 1));
    r = p && consumeToken(b, FI) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // elif_clause fi
  private static boolean if_command_5_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "if_command_5_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = elif_clause(b, l + 1);
    r = r && consumeToken(b, FI);
    exit_section_(b, m, null, r);
    return r;
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
  // '\n' | ';'
  public static boolean list_terminator(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "list_terminator")) return false;
    if (!nextTokenIs(b, "<list terminator>", LINEFEED, SEMI)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, LIST_TERMINATOR, "<list terminator>");
    r = consumeToken(b, LINEFEED);
    if (!r) r = consumeToken(b, SEMI);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // w | string | num
  static boolean literal(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "literal")) return false;
    boolean r;
    r = w(b, l + 1);
    if (!r) r = string(b, l + 1);
    if (!r) r = num(b, l + 1);
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
  // number | int
  static boolean num(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "num")) return false;
    if (!nextTokenIs(b, "", INT, NUMBER)) return false;
    boolean r;
    r = consumeToken(b, NUMBER);
    if (!r) r = consumeToken(b, INT);
    return r;
  }

  /* ********************************************************** */
  // w ('|' w)*
  public static boolean pattern(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, PATTERN, "<pattern>");
    r = w(b, l + 1);
    p = r; // pin = 1
    r = r && pattern_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // ('|' w)*
  private static boolean pattern_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!pattern_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "pattern_1", c)) break;
    }
    return true;
  }

  // '|' w
  private static boolean pattern_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern_1_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, PIPE);
    p = r; // pin = 1
    r = r && w(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // newlines pattern ')' compound_list
  //                  | newlines pattern ')' newlines
  //                  | newlines '(' pattern ')' compound_list
  //                  | newlines '(' pattern ')' newlines
  public static boolean pattern_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern_list")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PATTERN_LIST, "<pattern list>");
    r = pattern_list_0(b, l + 1);
    if (!r) r = pattern_list_1(b, l + 1);
    if (!r) r = pattern_list_2(b, l + 1);
    if (!r) r = pattern_list_3(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // newlines pattern ')' compound_list
  private static boolean pattern_list_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern_list_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = newlines(b, l + 1);
    r = r && pattern(b, l + 1);
    r = r && consumeToken(b, RIGHT_PAREN);
    r = r && compound_list(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // newlines pattern ')' newlines
  private static boolean pattern_list_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern_list_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = newlines(b, l + 1);
    r = r && pattern(b, l + 1);
    r = r && consumeToken(b, RIGHT_PAREN);
    r = r && newlines(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // newlines '(' pattern ')' compound_list
  private static boolean pattern_list_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern_list_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = newlines(b, l + 1);
    r = r && consumeToken(b, LEFT_PAREN);
    r = r && pattern(b, l + 1);
    r = r && consumeToken(b, RIGHT_PAREN);
    r = r && compound_list(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // newlines '(' pattern ')' newlines
  private static boolean pattern_list_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern_list_3")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = newlines(b, l + 1);
    r = r && consumeToken(b, LEFT_PAREN);
    r = r && pattern(b, l + 1);
    r = r && consumeToken(b, RIGHT_PAREN);
    r = r && newlines(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // command ('|' newlines command)*
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

  // ('|' newlines command)*
  private static boolean pipeline_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!pipeline_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "pipeline_1", c)) break;
    }
    return true;
  }

  // '|' newlines command
  private static boolean pipeline_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_1_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, PIPE);
    p = r; // pin = 1
    r = r && report_error_(b, newlines(b, l + 1));
    r = p && command(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // '!'? pipeline
  //                     | timespec '!'? pipeline
  //                     | '!' timespec pipeline
  public static boolean pipeline_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_command")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PIPELINE_COMMAND, "<pipeline command>");
    r = pipeline_command_0(b, l + 1);
    if (!r) r = pipeline_command_1(b, l + 1);
    if (!r) r = pipeline_command_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
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
  // ('<' | '>') '(' compound_list ')'
  public static boolean process_substitution(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "process_substitution")) return false;
    if (!nextTokenIs(b, "<process substitution>", GREATER_THAN, LESS_THAN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, PROCESS_SUBSTITUTION, "<process substitution>");
    r = process_substitution_0(b, l + 1);
    p = r; // pin = 1
    r = r && report_error_(b, consumeToken(b, LEFT_PAREN));
    r = p && report_error_(b, compound_list(b, l + 1)) && r;
    r = p && consumeToken(b, RIGHT_PAREN) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // '<' | '>'
  private static boolean process_substitution_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "process_substitution_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LESS_THAN);
    if (!r) r = consumeToken(b, GREATER_THAN);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '>' w
  //                 |  '<' w
  //                 |  num '>' w
  //                 |  num '<' w
  //                 |  '>>' w
  //                 |  num '>>' w
  //                 |  '<<' w
  //                 |  num '<<' w
  //                 |  '<&' num
  //                 |  num '<&' num
  //                 |  '>&' num
  //                 |  num '>&' num
  //                 |  '<&' w
  //                 |  num '<&' w
  //                 |  '>&' w
  //                 |  num '>&' w
  //                 |  '<<-' w
  //                 |  num '<<-' w
  //                 |  '>&' '-'
  //                 |  num '>&' '-'
  //                 |  '<&' '-'
  //                 |  num '<&' '-'
  //                 |  '&>' w
  //                 |  num '<>' w
  //                 |  '<>' w
  //                 |  '>|' w
  //                 |  num '>|' w
  public static boolean redirection(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, REDIRECTION, "<redirection>");
    r = redirection_0(b, l + 1);
    if (!r) r = redirection_1(b, l + 1);
    if (!r) r = redirection_2(b, l + 1);
    if (!r) r = redirection_3(b, l + 1);
    if (!r) r = redirection_4(b, l + 1);
    if (!r) r = redirection_5(b, l + 1);
    if (!r) r = redirection_6(b, l + 1);
    if (!r) r = redirection_7(b, l + 1);
    if (!r) r = redirection_8(b, l + 1);
    if (!r) r = redirection_9(b, l + 1);
    if (!r) r = redirection_10(b, l + 1);
    if (!r) r = redirection_11(b, l + 1);
    if (!r) r = redirection_12(b, l + 1);
    if (!r) r = redirection_13(b, l + 1);
    if (!r) r = redirection_14(b, l + 1);
    if (!r) r = redirection_15(b, l + 1);
    if (!r) r = redirection_16(b, l + 1);
    if (!r) r = redirection_17(b, l + 1);
    if (!r) r = parseTokens(b, 0, REDIRECT_GREATER_AMP, ARITH_MINUS);
    if (!r) r = redirection_19(b, l + 1);
    if (!r) r = parseTokens(b, 0, REDIRECT_LESS_AMP, ARITH_MINUS);
    if (!r) r = redirection_21(b, l + 1);
    if (!r) r = redirection_22(b, l + 1);
    if (!r) r = redirection_23(b, l + 1);
    if (!r) r = redirection_24(b, l + 1);
    if (!r) r = redirection_25(b, l + 1);
    if (!r) r = redirection_26(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // '>' w
  private static boolean redirection_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, GREATER_THAN);
    r = r && w(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '<' w
  private static boolean redirection_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LESS_THAN);
    r = r && w(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // num '>' w
  private static boolean redirection_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = num(b, l + 1);
    r = r && consumeToken(b, GREATER_THAN);
    r = r && w(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // num '<' w
  private static boolean redirection_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_3")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = num(b, l + 1);
    r = r && consumeToken(b, LESS_THAN);
    r = r && w(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '>>' w
  private static boolean redirection_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_4")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SHIFT_RIGHT);
    r = r && w(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // num '>>' w
  private static boolean redirection_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_5")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = num(b, l + 1);
    r = r && consumeToken(b, SHIFT_RIGHT);
    r = r && w(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '<<' w
  private static boolean redirection_6(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_6")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SHIFT_LEFT);
    r = r && w(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // num '<<' w
  private static boolean redirection_7(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_7")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = num(b, l + 1);
    r = r && consumeToken(b, SHIFT_LEFT);
    r = r && w(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '<&' num
  private static boolean redirection_8(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_8")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, REDIRECT_LESS_AMP);
    r = r && num(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // num '<&' num
  private static boolean redirection_9(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_9")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = num(b, l + 1);
    r = r && consumeToken(b, REDIRECT_LESS_AMP);
    r = r && num(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '>&' num
  private static boolean redirection_10(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_10")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, REDIRECT_GREATER_AMP);
    r = r && num(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // num '>&' num
  private static boolean redirection_11(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_11")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = num(b, l + 1);
    r = r && consumeToken(b, REDIRECT_GREATER_AMP);
    r = r && num(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '<&' w
  private static boolean redirection_12(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_12")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, REDIRECT_LESS_AMP);
    r = r && w(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // num '<&' w
  private static boolean redirection_13(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_13")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = num(b, l + 1);
    r = r && consumeToken(b, REDIRECT_LESS_AMP);
    r = r && w(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '>&' w
  private static boolean redirection_14(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_14")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, REDIRECT_GREATER_AMP);
    r = r && w(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // num '>&' w
  private static boolean redirection_15(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_15")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = num(b, l + 1);
    r = r && consumeToken(b, REDIRECT_GREATER_AMP);
    r = r && w(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '<<-' w
  private static boolean redirection_16(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_16")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, "<<-");
    r = r && w(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // num '<<-' w
  private static boolean redirection_17(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_17")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = num(b, l + 1);
    r = r && consumeToken(b, "<<-");
    r = r && w(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // num '>&' '-'
  private static boolean redirection_19(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_19")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = num(b, l + 1);
    r = r && consumeTokens(b, 0, REDIRECT_GREATER_AMP, ARITH_MINUS);
    exit_section_(b, m, null, r);
    return r;
  }

  // num '<&' '-'
  private static boolean redirection_21(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_21")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = num(b, l + 1);
    r = r && consumeTokens(b, 0, REDIRECT_LESS_AMP, ARITH_MINUS);
    exit_section_(b, m, null, r);
    return r;
  }

  // '&>' w
  private static boolean redirection_22(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_22")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, "&>");
    r = r && w(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // num '<>' w
  private static boolean redirection_23(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_23")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = num(b, l + 1);
    r = r && consumeToken(b, REDIRECT_LESS_GREATER);
    r = r && w(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '<>' w
  private static boolean redirection_24(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_24")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, REDIRECT_LESS_GREATER);
    r = r && w(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '>|' w
  private static boolean redirection_25(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_25")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, REDIRECT_GREATER_BAR);
    r = r && w(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // num '>|' w
  private static boolean redirection_26(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_26")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = num(b, l + 1);
    r = r && consumeToken(b, REDIRECT_GREATER_BAR);
    r = r && w(b, l + 1);
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
  // select w (';'? newlines block | newlines in_clause block)
  public static boolean select_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "select_command")) return false;
    if (!nextTokenIs(b, SELECT)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, SELECT_COMMAND, null);
    r = consumeToken(b, SELECT);
    p = r; // pin = 1
    r = r && report_error_(b, w(b, l + 1));
    r = p && select_command_2(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // ';'? newlines block | newlines in_clause block
  private static boolean select_command_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "select_command_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = select_command_2_0(b, l + 1);
    if (!r) r = select_command_2_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // ';'? newlines block
  private static boolean select_command_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "select_command_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = select_command_2_0_0(b, l + 1);
    r = r && newlines(b, l + 1);
    r = r && block(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // ';'?
  private static boolean select_command_2_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "select_command_2_0_0")) return false;
    consumeToken(b, SEMI);
    return true;
  }

  // newlines in_clause block
  private static boolean select_command_2_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "select_command_2_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = newlines(b, l + 1);
    r = r && in_clause(b, l + 1);
    r = r && block(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // for_command
  //                   | case_command
  //                   | while_command
  //                   | until_command
  //                   | select_command
  //                   | if_command
  //                   | subshell
  //                   | group_command
  //                   | function_def
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
    if (!r) r = subshell(b, l + 1);
    if (!r) r = group_command(b, l + 1);
    if (!r) r = function_def(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // '{' literal [":" ['+'|'-'] literal] '}'
  public static boolean shell_parameter_expansion(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "shell_parameter_expansion")) return false;
    if (!nextTokenIs(b, LEFT_CURLY)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LEFT_CURLY);
    r = r && literal(b, l + 1);
    r = r && shell_parameter_expansion_2(b, l + 1);
    r = r && consumeToken(b, RIGHT_CURLY);
    exit_section_(b, m, SHELL_PARAMETER_EXPANSION, r);
    return r;
  }

  // [":" ['+'|'-'] literal]
  private static boolean shell_parameter_expansion_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "shell_parameter_expansion_2")) return false;
    shell_parameter_expansion_2_0(b, l + 1);
    return true;
  }

  // ":" ['+'|'-'] literal
  private static boolean shell_parameter_expansion_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "shell_parameter_expansion_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COLON);
    r = r && shell_parameter_expansion_2_0_1(b, l + 1);
    r = r && literal(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // ['+'|'-']
  private static boolean shell_parameter_expansion_2_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "shell_parameter_expansion_2_0_1")) return false;
    shell_parameter_expansion_2_0_1_0(b, l + 1);
    return true;
  }

  // '+'|'-'
  private static boolean shell_parameter_expansion_2_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "shell_parameter_expansion_2_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ARITH_PLUS);
    if (!r) r = consumeToken(b, ARITH_MINUS);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // simple_command_element+
  public static boolean simple_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simple_command")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, SIMPLE_COMMAND, "<simple command>");
    r = simple_command_element(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!simple_command_element(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "simple_command", c)) break;
    }
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // assignment_word_rule | literal | redirection | composed_var | heredoc
  public static boolean simple_command_element(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simple_command_element")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, SIMPLE_COMMAND_ELEMENT, "<simple command element>");
    r = assignment_word_rule(b, l + 1);
    if (!r) r = literal(b, l + 1);
    if (!r) r = redirection(b, l + 1);
    if (!r) r = composed_var(b, l + 1);
    if (!r) r = heredoc(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
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
  // (string_begin (string_content|vars)* string_end) | STRING2
  public static boolean string(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "string")) return false;
    if (!nextTokenIs(b, "<string>", STRING2, STRING_BEGIN)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, STRING, "<string>");
    r = string_0(b, l + 1);
    if (!r) r = consumeToken(b, STRING2);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // string_begin (string_content|vars)* string_end
  private static boolean string_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "string_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, STRING_BEGIN);
    r = r && string_0_1(b, l + 1);
    r = r && consumeToken(b, STRING_END);
    exit_section_(b, m, null, r);
    return r;
  }

  // (string_content|vars)*
  private static boolean string_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "string_0_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!string_0_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "string_0_1", c)) break;
    }
    return true;
  }

  // string_content|vars
  private static boolean string_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "string_0_1_0")) return false;
    boolean r;
    r = consumeToken(b, STRING_CONTENT);
    if (!r) r = vars(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // '(' compound_list ')'
  public static boolean subshell(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "subshell")) return false;
    if (!nextTokenIs(b, LEFT_PAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LEFT_PAREN);
    r = r && compound_list(b, l + 1);
    r = r && consumeToken(b, RIGHT_PAREN);
    exit_section_(b, m, SUBSHELL, r);
    return r;
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
  // until compound_list do_block
  public static boolean until_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "until_command")) return false;
    if (!nextTokenIs(b, UNTIL)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, UNTIL_COMMAND, null);
    r = consumeToken(b, UNTIL);
    p = r; // pin = 1
    r = r && report_error_(b, compound_list(b, l + 1));
    r = p && do_block(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // variable | composed_var
  static boolean vars(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "vars")) return false;
    if (!nextTokenIs(b, "", DOLLAR, VARIABLE)) return false;
    boolean r;
    r = consumeToken(b, VARIABLE);
    if (!r) r = composed_var(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // word | variable | string | num
  static boolean w(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "w")) return false;
    boolean r;
    r = consumeToken(b, WORD);
    if (!r) r = consumeToken(b, VARIABLE);
    if (!r) r = string(b, l + 1);
    if (!r) r = num(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // while compound_list do_block
  public static boolean while_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "while_command")) return false;
    if (!nextTokenIs(b, WHILE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, WHILE_COMMAND, null);
    r = consumeToken(b, WHILE);
    p = r; // pin = 1
    r = r && report_error_(b, compound_list(b, l + 1));
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
  // 18: ATOM(literal_expression)
  // 19: ATOM(parentheses_expression)
  public static boolean expression(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "expression")) return false;
    addVariant(b, "<expression>");
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, "<expression>");
    r = logical_bitwise_negation_expression(b, l + 1);
    if (!r) r = unary_expression(b, l + 1);
    if (!r) r = pre_expression(b, l + 1);
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
      else if (g < 2 && consumeTokenSmart(b, "?")) {
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
      else if (g < 6 && consumeTokenSmart(b, "^")) {
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
    r = consumeTokenSmart(b, EQ);
    if (!r) r = consumeTokenSmart(b, "*=");
    if (!r) r = consumeTokenSmart(b, "/=");
    if (!r) r = consumeTokenSmart(b, "%=");
    if (!r) r = consumeTokenSmart(b, ADD_EQ);
    if (!r) r = consumeTokenSmart(b, "-=");
    if (!r) r = consumeTokenSmart(b, "<<=");
    if (!r) r = consumeTokenSmart(b, ">>=");
    if (!r) r = consumeTokenSmart(b, "&=");
    if (!r) r = consumeTokenSmart(b, "^=");
    if (!r) r = consumeTokenSmart(b, "|=");
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
    r = consumeTokenSmart(b, "==");
    if (!r) r = consumeTokenSmart(b, "!=");
    exit_section_(b, m, null, r);
    return r;
  }

  // '<=' | '>=' | '<' | '>'
  private static boolean comparison_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "comparison_expression_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, ARITH_LE);
    if (!r) r = consumeTokenSmart(b, ARITH_GE);
    if (!r) r = consumeTokenSmart(b, LESS_THAN);
    if (!r) r = consumeTokenSmart(b, GREATER_THAN);
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
    r = consumeTokenSmart(b, ARITH_PLUS);
    if (!r) r = consumeTokenSmart(b, ARITH_MINUS);
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
    if (!r) r = consumeTokenSmart(b, "~");
    exit_section_(b, m, null, r);
    return r;
  }

  public static boolean unary_expression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unary_expression")) return false;
    if (!nextTokenIsSmart(b, ARITH_MINUS, ARITH_PLUS)) return false;
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
    r = consumeTokenSmart(b, ARITH_MINUS);
    if (!r) r = consumeTokenSmart(b, ARITH_PLUS);
    exit_section_(b, m, null, r);
    return r;
  }

  public static boolean pre_expression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pre_expression")) return false;
    if (!nextTokenIsSmart(b, ARITH_MINUS_MINUS, ARITH_PLUS_PLUS)) return false;
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
    r = consumeTokenSmart(b, ARITH_MINUS_MINUS);
    if (!r) r = consumeTokenSmart(b, ARITH_PLUS_PLUS);
    exit_section_(b, m, null, r);
    return r;
  }

  // '--' | '++'
  private static boolean post_expression_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "post_expression_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, ARITH_MINUS_MINUS);
    if (!r) r = consumeTokenSmart(b, ARITH_PLUS_PLUS);
    exit_section_(b, m, null, r);
    return r;
  }

  // literal | assignment_word
  public static boolean literal_expression(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "literal_expression")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, LITERAL_EXPRESSION, "<literal expression>");
    r = literal(b, l + 1);
    if (!r) r = consumeTokenSmart(b, ASSIGNMENT_WORD);
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
