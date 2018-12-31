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
    if (t == CASE_CLAUSE) {
      r = case_clause(b, 0);
    }
    else if (t == CASE_CLAUSE_SEQUENCE) {
      r = case_clause_sequence(b, 0);
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
    else if (t == ELIF_CLAUSE) {
      r = elif_clause(b, 0);
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
    else if (t == IF_COMMAND) {
      r = if_command(b, 0);
    }
    else if (t == LIST) {
      r = list(b, 0);
    }
    else if (t == LIST_0) {
      r = list0(b, 0);
    }
    else if (t == LIST_1) {
      r = list1(b, 0);
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
    else {
      r = parse_root_(t, b, 0);
    }
    exit_section_(b, 0, m, t, r, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType t, PsiBuilder b, int l) {
    return file(b, l + 1);
  }

  public static final TokenSet[] EXTENDS_SETS_ = new TokenSet[] {
    create_token_set_(CASE_COMMAND, COMMAND, FOR_COMMAND, GROUP_COMMAND,
      IF_COMMAND, PIPELINE_COMMAND, SELECT_COMMAND, SHELL_COMMAND,
      SIMPLE_COMMAND),
  };

  /* ********************************************************** */
  // pattern_list
  //                 |  case_clause_sequence pattern_list
  public static boolean case_clause(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_clause")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, CASE_CLAUSE, "<case clause>");
    r = pattern_list(b, l + 1);
    if (!r) r = case_clause_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // case_clause_sequence pattern_list
  private static boolean case_clause_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_clause_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = case_clause_sequence(b, l + 1);
    r = r && pattern_list(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // (pattern_list ';;')+
  public static boolean case_clause_sequence(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_clause_sequence")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, CASE_CLAUSE_SEQUENCE, "<case clause sequence>");
    r = case_clause_sequence_0(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!case_clause_sequence_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "case_clause_sequence", c)) break;
    }
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // pattern_list ';;'
  private static boolean case_clause_sequence_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_clause_sequence_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = pattern_list(b, l + 1);
    r = r && consumeToken(b, CASE_END);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // case word newlines in newlines esac
  //                  |  case word newlines in case_clause_sequence newlines esac
  //                  |  case word newlines in case_clause esac
  public static boolean case_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_command")) return false;
    if (!nextTokenIs(b, CASE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = case_command_0(b, l + 1);
    if (!r) r = case_command_1(b, l + 1);
    if (!r) r = case_command_2(b, l + 1);
    exit_section_(b, m, CASE_COMMAND, r);
    return r;
  }

  // case word newlines in newlines esac
  private static boolean case_command_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_command_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, CASE, WORD);
    r = r && newlines(b, l + 1);
    r = r && consumeToken(b, IN);
    r = r && newlines(b, l + 1);
    r = r && consumeToken(b, ESAC);
    exit_section_(b, m, null, r);
    return r;
  }

  // case word newlines in case_clause_sequence newlines esac
  private static boolean case_command_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_command_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, CASE, WORD);
    r = r && newlines(b, l + 1);
    r = r && consumeToken(b, IN);
    r = r && case_clause_sequence(b, l + 1);
    r = r && newlines(b, l + 1);
    r = r && consumeToken(b, ESAC);
    exit_section_(b, m, null, r);
    return r;
  }

  // case word newlines in case_clause esac
  private static boolean case_command_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "case_command_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, CASE, WORD);
    r = r && newlines(b, l + 1);
    r = r && consumeToken(b, IN);
    r = r && case_clause(b, l + 1);
    r = r && consumeToken(b, ESAC);
    exit_section_(b, m, null, r);
    return r;
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
  // pipeline_command (
  //                      '&&' newlines pipeline_command
  //                    | '||' newlines pipeline_command
  //                    | '&' pipeline_command
  //                    | ';' pipeline_command
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
  //                    | ';' pipeline_command
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
  //                    | ';' pipeline_command
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

  // ';' pipeline_command
  private static boolean commands_list_1_0_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commands_list_1_0_3")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, SEMI);
    p = r; // pin = 1
    r = r && pipeline_command(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // '\n'+ list
  public static boolean compound_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "compound_list")) return false;
    if (!nextTokenIs(b, LINEFEED)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, COMPOUND_LIST, null);
    r = compound_list_0(b, l + 1);
    p = r; // pin = 1
    r = r && list(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // '\n'+
  private static boolean compound_list_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "compound_list_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LINEFEED);
    while (r) {
      int c = current_position_(b);
      if (!consumeToken(b, LINEFEED)) break;
      if (!empty_element_parsed_guard_(b, "compound_list_0", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
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
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ELIF);
    r = r && compound_list(b, l + 1);
    r = r && consumeToken(b, THEN);
    r = r && compound_list(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // elif compound_list then compound_list else compound_list
  private static boolean elif_clause_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "elif_clause_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ELIF);
    r = r && compound_list(b, l + 1);
    r = r && consumeToken(b, THEN);
    r = r && compound_list(b, l + 1);
    r = r && consumeToken(b, ELSE);
    r = r && compound_list(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // elif compound_list then compound_list elif_clause
  private static boolean elif_clause_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "elif_clause_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ELIF);
    r = r && compound_list(b, l + 1);
    r = r && consumeToken(b, THEN);
    r = r && compound_list(b, l + 1);
    r = r && elif_clause(b, l + 1);
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
  // for word newlines do compound_list done
  //             |  for word newlines '{' compound_list '}'
  //             |  for word ';' newlines do compound_list done
  //             |  for word ';' newlines '{' compound_list '}'
  //             |  for word newlines in word_list list_terminator newlines do compound_list done
  //             |  for word newlines in word_list list_terminator newlines '{' compound_list '}'
  public static boolean for_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_command")) return false;
    if (!nextTokenIs(b, FOR)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = for_command_0(b, l + 1);
    if (!r) r = for_command_1(b, l + 1);
    if (!r) r = for_command_2(b, l + 1);
    if (!r) r = for_command_3(b, l + 1);
    if (!r) r = for_command_4(b, l + 1);
    if (!r) r = for_command_5(b, l + 1);
    exit_section_(b, m, FOR_COMMAND, r);
    return r;
  }

  // for word newlines do compound_list done
  private static boolean for_command_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_command_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, FOR, WORD);
    r = r && newlines(b, l + 1);
    r = r && consumeToken(b, DO);
    r = r && compound_list(b, l + 1);
    r = r && consumeToken(b, DONE);
    exit_section_(b, m, null, r);
    return r;
  }

  // for word newlines '{' compound_list '}'
  private static boolean for_command_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_command_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, FOR, WORD);
    r = r && newlines(b, l + 1);
    r = r && consumeToken(b, LEFT_CURLY);
    r = r && compound_list(b, l + 1);
    r = r && consumeToken(b, RIGHT_CURLY);
    exit_section_(b, m, null, r);
    return r;
  }

  // for word ';' newlines do compound_list done
  private static boolean for_command_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_command_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, FOR, WORD, SEMI);
    r = r && newlines(b, l + 1);
    r = r && consumeToken(b, DO);
    r = r && compound_list(b, l + 1);
    r = r && consumeToken(b, DONE);
    exit_section_(b, m, null, r);
    return r;
  }

  // for word ';' newlines '{' compound_list '}'
  private static boolean for_command_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_command_3")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, FOR, WORD, SEMI);
    r = r && newlines(b, l + 1);
    r = r && consumeToken(b, LEFT_CURLY);
    r = r && compound_list(b, l + 1);
    r = r && consumeToken(b, RIGHT_CURLY);
    exit_section_(b, m, null, r);
    return r;
  }

  // for word newlines in word_list list_terminator newlines do compound_list done
  private static boolean for_command_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_command_4")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, FOR, WORD);
    r = r && newlines(b, l + 1);
    r = r && consumeToken(b, IN);
    r = r && word_list(b, l + 1);
    r = r && list_terminator(b, l + 1);
    r = r && newlines(b, l + 1);
    r = r && consumeToken(b, DO);
    r = r && compound_list(b, l + 1);
    r = r && consumeToken(b, DONE);
    exit_section_(b, m, null, r);
    return r;
  }

  // for word newlines in word_list list_terminator newlines '{' compound_list '}'
  private static boolean for_command_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "for_command_5")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, FOR, WORD);
    r = r && newlines(b, l + 1);
    r = r && consumeToken(b, IN);
    r = r && word_list(b, l + 1);
    r = r && list_terminator(b, l + 1);
    r = r && newlines(b, l + 1);
    r = r && consumeToken(b, LEFT_CURLY);
    r = r && compound_list(b, l + 1);
    r = r && consumeToken(b, RIGHT_CURLY);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // word '(' ')' newlines group_command
  //                  |  function word '(' ')' newlines group_command
  //                  |  function word newlines group_command
  public static boolean function_def(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_def")) return false;
    if (!nextTokenIs(b, "<function def>", FUNCTION, WORD)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FUNCTION_DEF, "<function def>");
    r = function_def_0(b, l + 1);
    if (!r) r = function_def_1(b, l + 1);
    if (!r) r = function_def_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // word '(' ')' newlines group_command
  private static boolean function_def_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_def_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, WORD, LEFT_PAREN, RIGHT_PAREN);
    r = r && newlines(b, l + 1);
    r = r && group_command(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // function word '(' ')' newlines group_command
  private static boolean function_def_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_def_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, FUNCTION, WORD, LEFT_PAREN, RIGHT_PAREN);
    r = r && newlines(b, l + 1);
    r = r && group_command(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // function word newlines group_command
  private static boolean function_def_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "function_def_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, FUNCTION, WORD);
    r = r && newlines(b, l + 1);
    r = r && group_command(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '{' list '}'
  public static boolean group_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "group_command")) return false;
    if (!nextTokenIs(b, LEFT_CURLY)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LEFT_CURLY);
    r = r && list(b, l + 1);
    r = r && consumeToken(b, RIGHT_CURLY);
    exit_section_(b, m, GROUP_COMMAND, r);
    return r;
  }

  /* ********************************************************** */
  // if compound_list then compound_list fi |
  //                if compound_list then compound_list else compound_list fi |
  //                if compound_list then compound_list elif_clause fi
  public static boolean if_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "if_command")) return false;
    if (!nextTokenIs(b, IF)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = if_command_0(b, l + 1);
    if (!r) r = if_command_1(b, l + 1);
    if (!r) r = if_command_2(b, l + 1);
    exit_section_(b, m, IF_COMMAND, r);
    return r;
  }

  // if compound_list then compound_list fi
  private static boolean if_command_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "if_command_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IF);
    r = r && compound_list(b, l + 1);
    r = r && consumeToken(b, THEN);
    r = r && compound_list(b, l + 1);
    r = r && consumeToken(b, FI);
    exit_section_(b, m, null, r);
    return r;
  }

  // if compound_list then compound_list else compound_list fi
  private static boolean if_command_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "if_command_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IF);
    r = r && compound_list(b, l + 1);
    r = r && consumeToken(b, THEN);
    r = r && compound_list(b, l + 1);
    r = r && consumeToken(b, ELSE);
    r = r && compound_list(b, l + 1);
    r = r && consumeToken(b, FI);
    exit_section_(b, m, null, r);
    return r;
  }

  // if compound_list then compound_list elif_clause fi
  private static boolean if_command_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "if_command_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IF);
    r = r && compound_list(b, l + 1);
    r = r && consumeToken(b, THEN);
    r = r && compound_list(b, l + 1);
    r = r && elif_clause(b, l + 1);
    r = r && consumeToken(b, FI);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // newlines list0
  public static boolean list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "list")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, LIST, "<list>");
    r = newlines(b, l + 1);
    r = r && list0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // list1 ('\n' | '&' | ';') newlines
  public static boolean list0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "list0")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, LIST_0, "<list 0>");
    r = list1(b, l + 1);
    r = r && list0_1(b, l + 1);
    r = r && newlines(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // '\n' | '&' | ';'
  private static boolean list0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "list0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LINEFEED);
    if (!r) r = consumeToken(b, AMP);
    if (!r) r = consumeToken(b, SEMI);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // pipeline_command [('&&'|  '||' |  '&' |  ';' |  '\n') newlines list1]
  public static boolean list1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "list1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, LIST_1, "<list 1>");
    r = pipeline_command(b, l + 1);
    r = r && list1_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // [('&&'|  '||' |  '&' |  ';' |  '\n') newlines list1]
  private static boolean list1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "list1_1")) return false;
    list1_1_0(b, l + 1);
    return true;
  }

  // ('&&'|  '||' |  '&' |  ';' |  '\n') newlines list1
  private static boolean list1_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "list1_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = list1_1_0_0(b, l + 1);
    r = r && newlines(b, l + 1);
    r = r && list1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '&&'|  '||' |  '&' |  ';' |  '\n'
  private static boolean list1_1_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "list1_1_0_0")) return false;
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
  // word ('|' word)*
  public static boolean pattern(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern")) return false;
    if (!nextTokenIs(b, WORD)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, PATTERN, null);
    r = consumeToken(b, WORD);
    p = r; // pin = 1
    r = r && pattern_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // ('|' word)*
  private static boolean pattern_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!pattern_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "pattern_1", c)) break;
    }
    return true;
  }

  // '|' word
  private static boolean pattern_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern_1_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeTokens(b, 1, PIPE, WORD);
    p = r; // pin = 1
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
  // pipeline
  //                     |  '!' pipeline
  //                     |  timespec pipeline
  //                     |  timespec '!' pipeline
  //                     |  '!' timespec pipeline
  public static boolean pipeline_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_command")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PIPELINE_COMMAND, "<pipeline command>");
    r = pipeline(b, l + 1);
    if (!r) r = pipeline_command_1(b, l + 1);
    if (!r) r = pipeline_command_2(b, l + 1);
    if (!r) r = pipeline_command_3(b, l + 1);
    if (!r) r = pipeline_command_4(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // '!' pipeline
  private static boolean pipeline_command_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_command_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, BANG);
    r = r && pipeline(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // timespec pipeline
  private static boolean pipeline_command_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_command_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = timespec(b, l + 1);
    r = r && pipeline(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // timespec '!' pipeline
  private static boolean pipeline_command_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_command_3")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = timespec(b, l + 1);
    r = r && consumeToken(b, BANG);
    r = r && pipeline(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // '!' timespec pipeline
  private static boolean pipeline_command_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pipeline_command_4")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, BANG);
    r = r && timespec(b, l + 1);
    r = r && pipeline(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // '>' word
  //                 |  '<' word
  //                 |  num '>' word
  //                 |  num '<' word
  //                 |  '>>' word
  //                 |  num '>>' word
  //                 |  '<<' word
  //                 |  num '<<' word
  //                 |  '<&' num
  //                 |  num '<&' num
  //                 |  '>&' num
  //                 |  num '>&' num
  //                 |  '<&' word
  //                 |  num '<&' word
  //                 |  '>&' word
  //                 |  num '>&' word
  //                 |  '<<-' word
  //                 |  num '<<-' word
  //                 |  '>&' '-'
  //                 |  num '>&' '-'
  //                 |  '<&' '-'
  //                 |  num '<&' '-'
  //                 |  '&>' word
  //                 |  num '<>' word
  //                 |  '<>' word
  //                 |  '>|' word
  //                 |  num '>|' word
  public static boolean redirection(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, REDIRECTION, "<redirection>");
    r = parseTokens(b, 0, GREATER_THAN, WORD);
    if (!r) r = parseTokens(b, 0, LESS_THAN, WORD);
    if (!r) r = redirection_2(b, l + 1);
    if (!r) r = redirection_3(b, l + 1);
    if (!r) r = parseTokens(b, 0, SHIFT_RIGHT, WORD);
    if (!r) r = redirection_5(b, l + 1);
    if (!r) r = parseTokens(b, 0, SHIFT_LEFT, WORD);
    if (!r) r = redirection_7(b, l + 1);
    if (!r) r = redirection_8(b, l + 1);
    if (!r) r = redirection_9(b, l + 1);
    if (!r) r = redirection_10(b, l + 1);
    if (!r) r = redirection_11(b, l + 1);
    if (!r) r = parseTokens(b, 0, REDIRECT_LESS_AMP, WORD);
    if (!r) r = redirection_13(b, l + 1);
    if (!r) r = parseTokens(b, 0, REDIRECT_GREATER_AMP, WORD);
    if (!r) r = redirection_15(b, l + 1);
    if (!r) r = redirection_16(b, l + 1);
    if (!r) r = redirection_17(b, l + 1);
    if (!r) r = parseTokens(b, 0, REDIRECT_GREATER_AMP, ARITH_MINUS);
    if (!r) r = redirection_19(b, l + 1);
    if (!r) r = parseTokens(b, 0, REDIRECT_LESS_AMP, ARITH_MINUS);
    if (!r) r = redirection_21(b, l + 1);
    if (!r) r = redirection_22(b, l + 1);
    if (!r) r = redirection_23(b, l + 1);
    if (!r) r = parseTokens(b, 0, REDIRECT_LESS_GREATER, WORD);
    if (!r) r = parseTokens(b, 0, REDIRECT_GREATER_BAR, WORD);
    if (!r) r = redirection_26(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // num '>' word
  private static boolean redirection_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = num(b, l + 1);
    r = r && consumeTokens(b, 0, GREATER_THAN, WORD);
    exit_section_(b, m, null, r);
    return r;
  }

  // num '<' word
  private static boolean redirection_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_3")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = num(b, l + 1);
    r = r && consumeTokens(b, 0, LESS_THAN, WORD);
    exit_section_(b, m, null, r);
    return r;
  }

  // num '>>' word
  private static boolean redirection_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_5")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = num(b, l + 1);
    r = r && consumeTokens(b, 0, SHIFT_RIGHT, WORD);
    exit_section_(b, m, null, r);
    return r;
  }

  // num '<<' word
  private static boolean redirection_7(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_7")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = num(b, l + 1);
    r = r && consumeTokens(b, 0, SHIFT_LEFT, WORD);
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

  // num '<&' word
  private static boolean redirection_13(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_13")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = num(b, l + 1);
    r = r && consumeTokens(b, 0, REDIRECT_LESS_AMP, WORD);
    exit_section_(b, m, null, r);
    return r;
  }

  // num '>&' word
  private static boolean redirection_15(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_15")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = num(b, l + 1);
    r = r && consumeTokens(b, 0, REDIRECT_GREATER_AMP, WORD);
    exit_section_(b, m, null, r);
    return r;
  }

  // '<<-' word
  private static boolean redirection_16(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_16")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, "<<-");
    r = r && consumeToken(b, WORD);
    exit_section_(b, m, null, r);
    return r;
  }

  // num '<<-' word
  private static boolean redirection_17(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_17")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = num(b, l + 1);
    r = r && consumeToken(b, "<<-");
    r = r && consumeToken(b, WORD);
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

  // '&>' word
  private static boolean redirection_22(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_22")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, "&>");
    r = r && consumeToken(b, WORD);
    exit_section_(b, m, null, r);
    return r;
  }

  // num '<>' word
  private static boolean redirection_23(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_23")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = num(b, l + 1);
    r = r && consumeTokens(b, 0, REDIRECT_LESS_GREATER, WORD);
    exit_section_(b, m, null, r);
    return r;
  }

  // num '>|' word
  private static boolean redirection_26(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "redirection_26")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = num(b, l + 1);
    r = r && consumeTokens(b, 0, REDIRECT_GREATER_BAR, WORD);
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
  // select word newlines do list done
  //                    |  select word newlines '{' list '}'
  //                    |  select word ';' newlines do list done
  //                    |  select word ';' newlines '{' list '}'
  //                    |  select word newlines in word_list list_terminator newlines do list done
  //                    |  select word newlines in word_list list_terminator newlines '{' list '}'
  public static boolean select_command(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "select_command")) return false;
    if (!nextTokenIs(b, SELECT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = select_command_0(b, l + 1);
    if (!r) r = select_command_1(b, l + 1);
    if (!r) r = select_command_2(b, l + 1);
    if (!r) r = select_command_3(b, l + 1);
    if (!r) r = select_command_4(b, l + 1);
    if (!r) r = select_command_5(b, l + 1);
    exit_section_(b, m, SELECT_COMMAND, r);
    return r;
  }

  // select word newlines do list done
  private static boolean select_command_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "select_command_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, SELECT, WORD);
    r = r && newlines(b, l + 1);
    r = r && consumeToken(b, DO);
    r = r && list(b, l + 1);
    r = r && consumeToken(b, DONE);
    exit_section_(b, m, null, r);
    return r;
  }

  // select word newlines '{' list '}'
  private static boolean select_command_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "select_command_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, SELECT, WORD);
    r = r && newlines(b, l + 1);
    r = r && consumeToken(b, LEFT_CURLY);
    r = r && list(b, l + 1);
    r = r && consumeToken(b, RIGHT_CURLY);
    exit_section_(b, m, null, r);
    return r;
  }

  // select word ';' newlines do list done
  private static boolean select_command_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "select_command_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, SELECT, WORD, SEMI);
    r = r && newlines(b, l + 1);
    r = r && consumeToken(b, DO);
    r = r && list(b, l + 1);
    r = r && consumeToken(b, DONE);
    exit_section_(b, m, null, r);
    return r;
  }

  // select word ';' newlines '{' list '}'
  private static boolean select_command_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "select_command_3")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, SELECT, WORD, SEMI);
    r = r && newlines(b, l + 1);
    r = r && consumeToken(b, LEFT_CURLY);
    r = r && list(b, l + 1);
    r = r && consumeToken(b, RIGHT_CURLY);
    exit_section_(b, m, null, r);
    return r;
  }

  // select word newlines in word_list list_terminator newlines do list done
  private static boolean select_command_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "select_command_4")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, SELECT, WORD);
    r = r && newlines(b, l + 1);
    r = r && consumeToken(b, IN);
    r = r && word_list(b, l + 1);
    r = r && list_terminator(b, l + 1);
    r = r && newlines(b, l + 1);
    r = r && consumeToken(b, DO);
    r = r && list(b, l + 1);
    r = r && consumeToken(b, DONE);
    exit_section_(b, m, null, r);
    return r;
  }

  // select word newlines in word_list list_terminator newlines '{' list '}'
  private static boolean select_command_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "select_command_5")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, SELECT, WORD);
    r = r && newlines(b, l + 1);
    r = r && consumeToken(b, IN);
    r = r && word_list(b, l + 1);
    r = r && list_terminator(b, l + 1);
    r = r && newlines(b, l + 1);
    r = r && consumeToken(b, LEFT_CURLY);
    r = r && list(b, l + 1);
    r = r && consumeToken(b, RIGHT_CURLY);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // for_command
  //                   | case_command
  //                   | while compound_list do compound_list done
  //                   | until compound_list do compound_list done
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
    if (!r) r = shell_command_2(b, l + 1);
    if (!r) r = shell_command_3(b, l + 1);
    if (!r) r = select_command(b, l + 1);
    if (!r) r = if_command(b, l + 1);
    if (!r) r = subshell(b, l + 1);
    if (!r) r = group_command(b, l + 1);
    if (!r) r = function_def(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // while compound_list do compound_list done
  private static boolean shell_command_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "shell_command_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, WHILE);
    r = r && compound_list(b, l + 1);
    r = r && consumeToken(b, DO);
    r = r && compound_list(b, l + 1);
    r = r && consumeToken(b, DONE);
    exit_section_(b, m, null, r);
    return r;
  }

  // until compound_list do compound_list done
  private static boolean shell_command_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "shell_command_3")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, UNTIL);
    r = r && compound_list(b, l + 1);
    r = r && consumeToken(b, DO);
    r = r && compound_list(b, l + 1);
    r = r && consumeToken(b, DONE);
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
  // word | assignment_word | redirection | string | variable | num
  public static boolean simple_command_element(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simple_command_element")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, SIMPLE_COMMAND_ELEMENT, "<simple command element>");
    r = consumeToken(b, WORD);
    if (!r) r = consumeToken(b, ASSIGNMENT_WORD);
    if (!r) r = redirection(b, l + 1);
    if (!r) r = string(b, l + 1);
    if (!r) r = consumeToken(b, VARIABLE);
    if (!r) r = num(b, l + 1);
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
  // string_begin string_content string_end
  public static boolean string(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "string")) return false;
    if (!nextTokenIs(b, STRING_BEGIN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, STRING_BEGIN, STRING_CONTENT, STRING_END);
    exit_section_(b, m, STRING, r);
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
  // word+
  static boolean word_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "word_list")) return false;
    if (!nextTokenIs(b, WORD)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, WORD);
    while (r) {
      int c = current_position_(b);
      if (!consumeToken(b, WORD)) break;
      if (!empty_element_parsed_guard_(b, "word_list", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

}
