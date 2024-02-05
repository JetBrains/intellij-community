// This is a generated file. Not intended for manual editing.
package com.jetbrains.performancePlugin.lang.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.jetbrains.performancePlugin.lang.psi.IJPerfElementTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class IJPerfParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType t, PsiBuilder b) {
    parseLight(t, b);
    return b.getTreeBuilt();
  }

  public void parseLight(IElementType t, PsiBuilder b) {
    boolean r;
    b = adapt_builder_(t, b, this, null);
    Marker m = enter_section_(b, 0, _COLLAPSE_, null);
    r = parse_root_(t, b);
    exit_section_(b, 0, m, t, r, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType t, PsiBuilder b) {
    return parse_root_(t, b, 0);
  }

  static boolean parse_root_(IElementType t, PsiBuilder b, int l) {
    return scriptFile(b, l + 1);
  }

  /* ********************************************************** */
  // commandName optionList?
  public static boolean commandLine(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commandLine")) return false;
    if (!nextTokenIs(b, COMMAND)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = commandName(b, l + 1);
    r = r && commandLine_1(b, l + 1);
    exit_section_(b, m, COMMAND_LINE, r);
    return r;
  }

  // optionList?
  private static boolean commandLine_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commandLine_1")) return false;
    optionList(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // COMMAND
  public static boolean commandName(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "commandName")) return false;
    if (!nextTokenIs(b, COMMAND)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMAND);
    exit_section_(b, m, COMMAND_NAME, r);
    return r;
  }

  /* ********************************************************** */
  // NUMBER PIPE TEXT
  public static boolean delayTypingOption(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "delayTypingOption")) return false;
    if (!nextTokenIs(b, NUMBER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, NUMBER, PIPE, TEXT);
    exit_section_(b, m, DELAY_TYPING_OPTION, r);
    return r;
  }

  /* ********************************************************** */
  // NUMBER OPTIONS_SEPARATOR NUMBER
  public static boolean gotoOption(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "gotoOption")) return false;
    if (!nextTokenIs(b, NUMBER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, NUMBER, OPTIONS_SEPARATOR, NUMBER);
    exit_section_(b, m, GOTO_OPTION, r);
    return r;
  }

  /* ********************************************************** */
  // simpleOption | gotoOption | delayTypingOption | FILE_PATH | NUMBER
  public static boolean option(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, OPTION, "<option>");
    r = simpleOption(b, l + 1);
    if (!r) r = gotoOption(b, l + 1);
    if (!r) r = delayTypingOption(b, l + 1);
    if (!r) r = consumeToken(b, FILE_PATH);
    if (!r) r = consumeToken(b, NUMBER);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // option (OPTIONS_SEPARATOR option)*
  public static boolean optionList(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "optionList")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, OPTION_LIST, "<option list>");
    r = option(b, l + 1);
    r = r && optionList_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (OPTIONS_SEPARATOR option)*
  private static boolean optionList_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "optionList_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!optionList_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "optionList_1", c)) break;
    }
    return true;
  }

  // OPTIONS_SEPARATOR option
  private static boolean optionList_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "optionList_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, OPTIONS_SEPARATOR);
    r = r && option(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // statement*
  static boolean scriptFile(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "scriptFile")) return false;
    while (true) {
      int c = current_position_(b);
      if (!statement(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "scriptFile", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // IDENTIFIER (ASSIGNMENT_OPERATOR (IDENTIFIER|NUMBER|FILE_PATH))?
  public static boolean simpleOption(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simpleOption")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    r = r && simpleOption_1(b, l + 1);
    exit_section_(b, m, SIMPLE_OPTION, r);
    return r;
  }

  // (ASSIGNMENT_OPERATOR (IDENTIFIER|NUMBER|FILE_PATH))?
  private static boolean simpleOption_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simpleOption_1")) return false;
    simpleOption_1_0(b, l + 1);
    return true;
  }

  // ASSIGNMENT_OPERATOR (IDENTIFIER|NUMBER|FILE_PATH)
  private static boolean simpleOption_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simpleOption_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ASSIGNMENT_OPERATOR);
    r = r && simpleOption_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // IDENTIFIER|NUMBER|FILE_PATH
  private static boolean simpleOption_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simpleOption_1_0_1")) return false;
    boolean r;
    r = consumeToken(b, IDENTIFIER);
    if (!r) r = consumeToken(b, NUMBER);
    if (!r) r = consumeToken(b, FILE_PATH);
    return r;
  }

  /* ********************************************************** */
  // commandLine|COMMENT
  public static boolean statement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statement")) return false;
    if (!nextTokenIs(b, "<statement>", COMMAND, COMMENT)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, STATEMENT, "<statement>");
    r = commandLine(b, l + 1);
    if (!r) r = consumeToken(b, COMMENT);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

}
