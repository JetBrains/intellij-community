// This is a generated file. Not intended for manual editing.
package com.intellij.python.requirements.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.intellij.python.requirements.parser.psi.RequirementsTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class RequirementsParser implements PsiParser, LightPsiParser {

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
    return requirementsFile(b, l + 1);
  }

  /* ********************************************************** */
  // LSBRACE (IPv6address | IPvFuture) RSBRACE
  static boolean IP_literal(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IP_literal")) return false;
    if (!nextTokenIs(b, LSBRACE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LSBRACE);
    r = r && IP_literal_1(b, l + 1);
    r = r && consumeToken(b, RSBRACE);
    exit_section_(b, m, null, r);
    return r;
  }

  // IPv6address | IPvFuture
  private static boolean IP_literal_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IP_literal_1")) return false;
    boolean r;
    r = IPv6address(b, l + 1);
    if (!r) r = IPvFuture(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // dec_octet DOT dec_octet DOT dec_octet DOT dec_octet
  static boolean IPv4address(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv4address")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = dec_octet(b, l + 1);
    r = r && consumeToken(b, DOT);
    r = r && dec_octet(b, l + 1);
    r = r && consumeToken(b, DOT);
    r = r && dec_octet(b, l + 1);
    r = r && consumeToken(b, DOT);
    r = r && dec_octet(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // h16_colon h16_colon h16_colon h16_colon h16_colon h16_colon ls32
  //                   | COLON COLON h16_colon h16_colon h16_colon h16_colon h16_colon ls32
  //                   | h16?  COLON COLON h16_colon h16_colon h16_colon h16_colon ls32
  //                   | (h16_colon? h16)? COLON COLON h16_colon h16_colon h16_colon ls32
  //                   | (h16_colon? h16_colon? h16 )? COLON COLON h16_colon h16_colon ls32
  //                   | (h16_colon? h16_colon? h16_colon? h16 )? COLON COLON h16_colon ls32
  //                   | (h16_colon? h16_colon? h16_colon? h16_colon? h16 )? COLON COLON ls32
  //                   | (h16_colon? h16_colon? h16_colon? h16_colon? h16_colon? h16 )? COLON COLON h16
  //                   | (h16_colon? h16_colon? h16_colon? h16_colon? h16_colon? h16_colon? h16 )? COLON COLON
  static boolean IPv6address(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = IPv6address_0(b, l + 1);
    if (!r) r = IPv6address_1(b, l + 1);
    if (!r) r = IPv6address_2(b, l + 1);
    if (!r) r = IPv6address_3(b, l + 1);
    if (!r) r = IPv6address_4(b, l + 1);
    if (!r) r = IPv6address_5(b, l + 1);
    if (!r) r = IPv6address_6(b, l + 1);
    if (!r) r = IPv6address_7(b, l + 1);
    if (!r) r = IPv6address_8(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // h16_colon h16_colon h16_colon h16_colon h16_colon h16_colon ls32
  private static boolean IPv6address_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = h16_colon(b, l + 1);
    r = r && h16_colon(b, l + 1);
    r = r && h16_colon(b, l + 1);
    r = r && h16_colon(b, l + 1);
    r = r && h16_colon(b, l + 1);
    r = r && h16_colon(b, l + 1);
    r = r && ls32(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // COLON COLON h16_colon h16_colon h16_colon h16_colon h16_colon ls32
  private static boolean IPv6address_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, COLON, COLON);
    r = r && h16_colon(b, l + 1);
    r = r && h16_colon(b, l + 1);
    r = r && h16_colon(b, l + 1);
    r = r && h16_colon(b, l + 1);
    r = r && h16_colon(b, l + 1);
    r = r && ls32(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // h16?  COLON COLON h16_colon h16_colon h16_colon h16_colon ls32
  private static boolean IPv6address_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = IPv6address_2_0(b, l + 1);
    r = r && consumeTokens(b, 0, COLON, COLON);
    r = r && h16_colon(b, l + 1);
    r = r && h16_colon(b, l + 1);
    r = r && h16_colon(b, l + 1);
    r = r && h16_colon(b, l + 1);
    r = r && ls32(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // h16?
  private static boolean IPv6address_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_2_0")) return false;
    h16(b, l + 1);
    return true;
  }

  // (h16_colon? h16)? COLON COLON h16_colon h16_colon h16_colon ls32
  private static boolean IPv6address_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_3")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = IPv6address_3_0(b, l + 1);
    r = r && consumeTokens(b, 0, COLON, COLON);
    r = r && h16_colon(b, l + 1);
    r = r && h16_colon(b, l + 1);
    r = r && h16_colon(b, l + 1);
    r = r && ls32(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (h16_colon? h16)?
  private static boolean IPv6address_3_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_3_0")) return false;
    IPv6address_3_0_0(b, l + 1);
    return true;
  }

  // h16_colon? h16
  private static boolean IPv6address_3_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_3_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = IPv6address_3_0_0_0(b, l + 1);
    r = r && h16(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // h16_colon?
  private static boolean IPv6address_3_0_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_3_0_0_0")) return false;
    h16_colon(b, l + 1);
    return true;
  }

  // (h16_colon? h16_colon? h16 )? COLON COLON h16_colon h16_colon ls32
  private static boolean IPv6address_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_4")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = IPv6address_4_0(b, l + 1);
    r = r && consumeTokens(b, 0, COLON, COLON);
    r = r && h16_colon(b, l + 1);
    r = r && h16_colon(b, l + 1);
    r = r && ls32(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (h16_colon? h16_colon? h16 )?
  private static boolean IPv6address_4_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_4_0")) return false;
    IPv6address_4_0_0(b, l + 1);
    return true;
  }

  // h16_colon? h16_colon? h16
  private static boolean IPv6address_4_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_4_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = IPv6address_4_0_0_0(b, l + 1);
    r = r && IPv6address_4_0_0_1(b, l + 1);
    r = r && h16(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // h16_colon?
  private static boolean IPv6address_4_0_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_4_0_0_0")) return false;
    h16_colon(b, l + 1);
    return true;
  }

  // h16_colon?
  private static boolean IPv6address_4_0_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_4_0_0_1")) return false;
    h16_colon(b, l + 1);
    return true;
  }

  // (h16_colon? h16_colon? h16_colon? h16 )? COLON COLON h16_colon ls32
  private static boolean IPv6address_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_5")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = IPv6address_5_0(b, l + 1);
    r = r && consumeTokens(b, 0, COLON, COLON);
    r = r && h16_colon(b, l + 1);
    r = r && ls32(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (h16_colon? h16_colon? h16_colon? h16 )?
  private static boolean IPv6address_5_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_5_0")) return false;
    IPv6address_5_0_0(b, l + 1);
    return true;
  }

  // h16_colon? h16_colon? h16_colon? h16
  private static boolean IPv6address_5_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_5_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = IPv6address_5_0_0_0(b, l + 1);
    r = r && IPv6address_5_0_0_1(b, l + 1);
    r = r && IPv6address_5_0_0_2(b, l + 1);
    r = r && h16(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // h16_colon?
  private static boolean IPv6address_5_0_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_5_0_0_0")) return false;
    h16_colon(b, l + 1);
    return true;
  }

  // h16_colon?
  private static boolean IPv6address_5_0_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_5_0_0_1")) return false;
    h16_colon(b, l + 1);
    return true;
  }

  // h16_colon?
  private static boolean IPv6address_5_0_0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_5_0_0_2")) return false;
    h16_colon(b, l + 1);
    return true;
  }

  // (h16_colon? h16_colon? h16_colon? h16_colon? h16 )? COLON COLON ls32
  private static boolean IPv6address_6(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_6")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = IPv6address_6_0(b, l + 1);
    r = r && consumeTokens(b, 0, COLON, COLON);
    r = r && ls32(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (h16_colon? h16_colon? h16_colon? h16_colon? h16 )?
  private static boolean IPv6address_6_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_6_0")) return false;
    IPv6address_6_0_0(b, l + 1);
    return true;
  }

  // h16_colon? h16_colon? h16_colon? h16_colon? h16
  private static boolean IPv6address_6_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_6_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = IPv6address_6_0_0_0(b, l + 1);
    r = r && IPv6address_6_0_0_1(b, l + 1);
    r = r && IPv6address_6_0_0_2(b, l + 1);
    r = r && IPv6address_6_0_0_3(b, l + 1);
    r = r && h16(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // h16_colon?
  private static boolean IPv6address_6_0_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_6_0_0_0")) return false;
    h16_colon(b, l + 1);
    return true;
  }

  // h16_colon?
  private static boolean IPv6address_6_0_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_6_0_0_1")) return false;
    h16_colon(b, l + 1);
    return true;
  }

  // h16_colon?
  private static boolean IPv6address_6_0_0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_6_0_0_2")) return false;
    h16_colon(b, l + 1);
    return true;
  }

  // h16_colon?
  private static boolean IPv6address_6_0_0_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_6_0_0_3")) return false;
    h16_colon(b, l + 1);
    return true;
  }

  // (h16_colon? h16_colon? h16_colon? h16_colon? h16_colon? h16 )? COLON COLON h16
  private static boolean IPv6address_7(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_7")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = IPv6address_7_0(b, l + 1);
    r = r && consumeTokens(b, 0, COLON, COLON);
    r = r && h16(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (h16_colon? h16_colon? h16_colon? h16_colon? h16_colon? h16 )?
  private static boolean IPv6address_7_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_7_0")) return false;
    IPv6address_7_0_0(b, l + 1);
    return true;
  }

  // h16_colon? h16_colon? h16_colon? h16_colon? h16_colon? h16
  private static boolean IPv6address_7_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_7_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = IPv6address_7_0_0_0(b, l + 1);
    r = r && IPv6address_7_0_0_1(b, l + 1);
    r = r && IPv6address_7_0_0_2(b, l + 1);
    r = r && IPv6address_7_0_0_3(b, l + 1);
    r = r && IPv6address_7_0_0_4(b, l + 1);
    r = r && h16(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // h16_colon?
  private static boolean IPv6address_7_0_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_7_0_0_0")) return false;
    h16_colon(b, l + 1);
    return true;
  }

  // h16_colon?
  private static boolean IPv6address_7_0_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_7_0_0_1")) return false;
    h16_colon(b, l + 1);
    return true;
  }

  // h16_colon?
  private static boolean IPv6address_7_0_0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_7_0_0_2")) return false;
    h16_colon(b, l + 1);
    return true;
  }

  // h16_colon?
  private static boolean IPv6address_7_0_0_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_7_0_0_3")) return false;
    h16_colon(b, l + 1);
    return true;
  }

  // h16_colon?
  private static boolean IPv6address_7_0_0_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_7_0_0_4")) return false;
    h16_colon(b, l + 1);
    return true;
  }

  // (h16_colon? h16_colon? h16_colon? h16_colon? h16_colon? h16_colon? h16 )? COLON COLON
  private static boolean IPv6address_8(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_8")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = IPv6address_8_0(b, l + 1);
    r = r && consumeTokens(b, 0, COLON, COLON);
    exit_section_(b, m, null, r);
    return r;
  }

  // (h16_colon? h16_colon? h16_colon? h16_colon? h16_colon? h16_colon? h16 )?
  private static boolean IPv6address_8_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_8_0")) return false;
    IPv6address_8_0_0(b, l + 1);
    return true;
  }

  // h16_colon? h16_colon? h16_colon? h16_colon? h16_colon? h16_colon? h16
  private static boolean IPv6address_8_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_8_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = IPv6address_8_0_0_0(b, l + 1);
    r = r && IPv6address_8_0_0_1(b, l + 1);
    r = r && IPv6address_8_0_0_2(b, l + 1);
    r = r && IPv6address_8_0_0_3(b, l + 1);
    r = r && IPv6address_8_0_0_4(b, l + 1);
    r = r && IPv6address_8_0_0_5(b, l + 1);
    r = r && h16(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // h16_colon?
  private static boolean IPv6address_8_0_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_8_0_0_0")) return false;
    h16_colon(b, l + 1);
    return true;
  }

  // h16_colon?
  private static boolean IPv6address_8_0_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_8_0_0_1")) return false;
    h16_colon(b, l + 1);
    return true;
  }

  // h16_colon?
  private static boolean IPv6address_8_0_0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_8_0_0_2")) return false;
    h16_colon(b, l + 1);
    return true;
  }

  // h16_colon?
  private static boolean IPv6address_8_0_0_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_8_0_0_3")) return false;
    h16_colon(b, l + 1);
    return true;
  }

  // h16_colon?
  private static boolean IPv6address_8_0_0_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_8_0_0_4")) return false;
    h16_colon(b, l + 1);
    return true;
  }

  // h16_colon?
  private static boolean IPv6address_8_0_0_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address_8_0_0_5")) return false;
    h16_colon(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // 'v' hexdig+ DOT (URI_UNRESERVED | URI_SUB_DELIMITER | DOLLAR_SIGN | COLON)+
  static boolean IPvFuture(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPvFuture")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, "v");
    r = r && IPvFuture_1(b, l + 1);
    r = r && consumeToken(b, DOT);
    r = r && IPvFuture_3(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // hexdig+
  private static boolean IPvFuture_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPvFuture_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = hexdig(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!hexdig(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "IPvFuture_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // (URI_UNRESERVED | URI_SUB_DELIMITER | DOLLAR_SIGN | COLON)+
  private static boolean IPvFuture_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPvFuture_3")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = IPvFuture_3_0(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!IPvFuture_3_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "IPvFuture_3", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // URI_UNRESERVED | URI_SUB_DELIMITER | DOLLAR_SIGN | COLON
  private static boolean IPvFuture_3_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPvFuture_3_0")) return false;
    boolean r;
    r = consumeToken(b, URI_UNRESERVED);
    if (!r) r = consumeToken(b, URI_SUB_DELIMITER);
    if (!r) r = consumeToken(b, DOLLAR_SIGN);
    if (!r) r = consumeToken(b, COLON);
    return r;
  }

  /* ********************************************************** */
  // (PATH_SEPARATOR PATH_SEGMENT?)+
  static boolean abs_path(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "abs_path")) return false;
    if (!nextTokenIs(b, PATH_SEPARATOR)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = abs_path_0(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!abs_path_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "abs_path", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // PATH_SEPARATOR PATH_SEGMENT?
  private static boolean abs_path_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "abs_path_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, PATH_SEPARATOR);
    r = r && abs_path_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // PATH_SEGMENT?
  private static boolean abs_path_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "abs_path_0_1")) return false;
    consumeToken(b, PATH_SEGMENT);
    return true;
  }

  /* ********************************************************** */
  // uri | git_uri | bzr_launchpad_uri
  static boolean any_uri(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "any_uri")) return false;
    boolean r;
    r = uri(b, l + 1);
    if (!r) r = git_uri(b, l + 1);
    if (!r) r = bzr_launchpad_uri(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // (userinfo AT)? host (COLON port)?
  public static boolean authority(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "authority")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, AUTHORITY, "<authority>");
    r = authority_0(b, l + 1);
    r = r && host(b, l + 1);
    r = r && authority_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (userinfo AT)?
  private static boolean authority_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "authority_0")) return false;
    authority_0_0(b, l + 1);
    return true;
  }

  // userinfo AT
  private static boolean authority_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "authority_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = userinfo(b, l + 1);
    r = r && consumeToken(b, AT);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COLON port)?
  private static boolean authority_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "authority_2")) return false;
    authority_2_0(b, l + 1);
    return true;
  }

  // COLON port
  private static boolean authority_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "authority_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COLON);
    r = r && port(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // BZR_LAUNCHPAD_SCHEME package_name (AT vcs_revision)? (SHARP fragment)?
  public static boolean bzr_launchpad_uri(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bzr_launchpad_uri")) return false;
    if (!nextTokenIs(b, BZR_LAUNCHPAD_SCHEME)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, BZR_LAUNCHPAD_URI, null);
    r = consumeToken(b, BZR_LAUNCHPAD_SCHEME);
    p = r; // pin = 1
    r = r && report_error_(b, package_name(b, l + 1));
    r = p && report_error_(b, bzr_launchpad_uri_2(b, l + 1)) && r;
    r = p && bzr_launchpad_uri_3(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (AT vcs_revision)?
  private static boolean bzr_launchpad_uri_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bzr_launchpad_uri_2")) return false;
    bzr_launchpad_uri_2_0(b, l + 1);
    return true;
  }

  // AT vcs_revision
  private static boolean bzr_launchpad_uri_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bzr_launchpad_uri_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, AT);
    r = r && vcs_revision(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (SHARP fragment)?
  private static boolean bzr_launchpad_uri_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bzr_launchpad_uri_3")) return false;
    bzr_launchpad_uri_3_0(b, l + 1);
    return true;
  }

  // SHARP fragment
  private static boolean bzr_launchpad_uri_3_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bzr_launchpad_uri_3_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SHARP);
    r = r && fragment(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // DIGIT // 0-9
  //                   | nz DIGIT // 10-99
  //                   | "1" DIGIT DIGIT // 100-199
  //                   | "2" ("0" | "1" | "2" | "3" | "4") DIGIT // 200-249
  //                   | "2" "5" ("0" | "1" | "2" | "3" | "4" | "5")
  static boolean dec_octet(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dec_octet")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, DIGIT);
    if (!r) r = dec_octet_1(b, l + 1);
    if (!r) r = dec_octet_2(b, l + 1);
    if (!r) r = dec_octet_3(b, l + 1);
    if (!r) r = dec_octet_4(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // nz DIGIT
  private static boolean dec_octet_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dec_octet_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = nz(b, l + 1);
    r = r && consumeToken(b, DIGIT);
    exit_section_(b, m, null, r);
    return r;
  }

  // "1" DIGIT DIGIT
  private static boolean dec_octet_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dec_octet_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, "1");
    r = r && consumeTokens(b, 0, DIGIT, DIGIT);
    exit_section_(b, m, null, r);
    return r;
  }

  // "2" ("0" | "1" | "2" | "3" | "4") DIGIT
  private static boolean dec_octet_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dec_octet_3")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, "2");
    r = r && dec_octet_3_1(b, l + 1);
    r = r && consumeToken(b, DIGIT);
    exit_section_(b, m, null, r);
    return r;
  }

  // "0" | "1" | "2" | "3" | "4"
  private static boolean dec_octet_3_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dec_octet_3_1")) return false;
    boolean r;
    r = consumeToken(b, "0");
    if (!r) r = consumeToken(b, "1");
    if (!r) r = consumeToken(b, "2");
    if (!r) r = consumeToken(b, "3");
    if (!r) r = consumeToken(b, "4");
    return r;
  }

  // "2" "5" ("0" | "1" | "2" | "3" | "4" | "5")
  private static boolean dec_octet_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dec_octet_4")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, "2");
    r = r && consumeToken(b, "5");
    r = r && dec_octet_4_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // "0" | "1" | "2" | "3" | "4" | "5"
  private static boolean dec_octet_4_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dec_octet_4_2")) return false;
    boolean r;
    r = consumeToken(b, "0");
    if (!r) r = consumeToken(b, "1");
    if (!r) r = consumeToken(b, "2");
    if (!r) r = consumeToken(b, "3");
    if (!r) r = consumeToken(b, "4");
    if (!r) r = consumeToken(b, "5");
    return r;
  }

  /* ********************************************************** */
  // EDITABLE_OPTION_IDENTIFIER
  public static boolean editable_option(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "editable_option")) return false;
    if (!nextTokenIs(b, EDITABLE_OPTION_IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, EDITABLE_OPTION_IDENTIFIER);
    exit_section_(b, m, EDITABLE_OPTION, r);
    return r;
  }

  /* ********************************************************** */
  // ENV_VARIABLE_START variable_name ENV_VARIABLE_END
  public static boolean env_variable(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "env_variable")) return false;
    if (!nextTokenIs(b, ENV_VARIABLE_START)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ENV_VARIABLE_START);
    r = r && variable_name(b, l + 1);
    r = r && consumeToken(b, ENV_VARIABLE_END);
    exit_section_(b, m, ENV_VARIABLE, r);
    return r;
  }

  /* ********************************************************** */
  // LSBRACE extras_list RSBRACE
  public static boolean extras(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "extras")) return false;
    if (!nextTokenIs(b, LSBRACE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, EXTRAS, null);
    r = consumeToken(b, LSBRACE);
    p = r; // pin = 1
    r = r && report_error_(b, extras_list(b, l + 1));
    r = p && consumeToken(b, RSBRACE) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // package_name (COMMA package_name)*
  public static boolean extras_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "extras_list")) return false;
    if (!nextTokenIs(b, PACKAGE_NAME_TOKEN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = package_name(b, l + 1);
    r = r && extras_list_1(b, l + 1);
    exit_section_(b, m, EXTRAS_LIST, r);
    return r;
  }

  // (COMMA package_name)*
  private static boolean extras_list_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "extras_list_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!extras_list_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "extras_list_1", c)) break;
    }
    return true;
  }

  // COMMA package_name
  private static boolean extras_list_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "extras_list_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && package_name(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // (pchar | QUESTION_MARK)*
  public static boolean fragment(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fragment")) return false;
    Marker m = enter_section_(b, l, _NONE_, FRAGMENT, "<fragment>");
    while (true) {
      int c = current_position_(b);
      if (!fragment_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "fragment", c)) break;
    }
    exit_section_(b, l, m, true, false, null);
    return true;
  }

  // pchar | QUESTION_MARK
  private static boolean fragment_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fragment_0")) return false;
    boolean r;
    r = pchar(b, l + 1);
    if (!r) r = consumeToken(b, QUESTION_MARK);
    return r;
  }

  /* ********************************************************** */
  // GIT_URI_SCHEME AT host COLON git_uri_path (SHARP fragment)?
  public static boolean git_uri(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "git_uri")) return false;
    if (!nextTokenIs(b, GIT_URI_SCHEME)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, GIT_URI, null);
    r = consumeTokens(b, 1, GIT_URI_SCHEME, AT);
    p = r; // pin = 1
    r = r && report_error_(b, host(b, l + 1));
    r = p && report_error_(b, consumeToken(b, COLON)) && r;
    r = p && report_error_(b, git_uri_path(b, l + 1)) && r;
    r = p && git_uri_5(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (SHARP fragment)?
  private static boolean git_uri_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "git_uri_5")) return false;
    git_uri_5_0(b, l + 1);
    return true;
  }

  // SHARP fragment
  private static boolean git_uri_5_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "git_uri_5_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SHARP);
    r = r && fragment(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // git_uri_path_segment (SLASH git_uri_path_segment?)* (AT vcs_revision)?
  public static boolean git_uri_path(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "git_uri_path")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, GIT_URI_PATH, "<git uri path>");
    r = git_uri_path_segment(b, l + 1);
    r = r && git_uri_path_1(b, l + 1);
    r = r && git_uri_path_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (SLASH git_uri_path_segment?)*
  private static boolean git_uri_path_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "git_uri_path_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!git_uri_path_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "git_uri_path_1", c)) break;
    }
    return true;
  }

  // SLASH git_uri_path_segment?
  private static boolean git_uri_path_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "git_uri_path_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SLASH);
    r = r && git_uri_path_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // git_uri_path_segment?
  private static boolean git_uri_path_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "git_uri_path_1_0_1")) return false;
    git_uri_path_segment(b, l + 1);
    return true;
  }

  // (AT vcs_revision)?
  private static boolean git_uri_path_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "git_uri_path_2")) return false;
    git_uri_path_2_0(b, l + 1);
    return true;
  }

  // AT vcs_revision
  private static boolean git_uri_path_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "git_uri_path_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, AT);
    r = r && vcs_revision(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // (LETTER | DIGIT | UNDERSCORE | HYPHEN | DOT | URI_UNRESERVED | PCT_ENCODED | URI_SUB_DELIMITER)*
  public static boolean git_uri_path_segment(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "git_uri_path_segment")) return false;
    Marker m = enter_section_(b, l, _NONE_, GIT_URI_PATH_SEGMENT, "<git uri path segment>");
    while (true) {
      int c = current_position_(b);
      if (!git_uri_path_segment_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "git_uri_path_segment", c)) break;
    }
    exit_section_(b, l, m, true, false, null);
    return true;
  }

  // LETTER | DIGIT | UNDERSCORE | HYPHEN | DOT | URI_UNRESERVED | PCT_ENCODED | URI_SUB_DELIMITER
  private static boolean git_uri_path_segment_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "git_uri_path_segment_0")) return false;
    boolean r;
    r = consumeToken(b, LETTER);
    if (!r) r = consumeToken(b, DIGIT);
    if (!r) r = consumeToken(b, UNDERSCORE);
    if (!r) r = consumeToken(b, HYPHEN);
    if (!r) r = consumeToken(b, DOT);
    if (!r) r = consumeToken(b, URI_UNRESERVED);
    if (!r) r = consumeToken(b, PCT_ENCODED);
    if (!r) r = consumeToken(b, URI_SUB_DELIMITER);
    return r;
  }

  /* ********************************************************** */
  // hexdig hexdig? hexdig? hexdig?
  static boolean h16(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "h16")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = hexdig(b, l + 1);
    r = r && h16_1(b, l + 1);
    r = r && h16_2(b, l + 1);
    r = r && h16_3(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // hexdig?
  private static boolean h16_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "h16_1")) return false;
    hexdig(b, l + 1);
    return true;
  }

  // hexdig?
  private static boolean h16_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "h16_2")) return false;
    hexdig(b, l + 1);
    return true;
  }

  // hexdig?
  private static boolean h16_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "h16_3")) return false;
    hexdig(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // h16 COLON
  static boolean h16_colon(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "h16_colon")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = h16(b, l + 1);
    r = r && consumeToken(b, COLON);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // DIGIT | 'a' | 'A' | 'b' | 'B' | 'c' | 'C' | 'd' | 'D' | 'e' | 'E' | 'f' | 'F'
  static boolean hexdig(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "hexdig")) return false;
    boolean r;
    r = consumeToken(b, DIGIT);
    if (!r) r = consumeToken(b, "a");
    if (!r) r = consumeToken(b, "A");
    if (!r) r = consumeToken(b, "b");
    if (!r) r = consumeToken(b, "B");
    if (!r) r = consumeToken(b, "c");
    if (!r) r = consumeToken(b, "C");
    if (!r) r = consumeToken(b, "d");
    if (!r) r = consumeToken(b, "D");
    if (!r) r = consumeToken(b, "e");
    if (!r) r = consumeToken(b, "E");
    if (!r) r = consumeToken(b, "f");
    if (!r) r = consumeToken(b, "F");
    return r;
  }

  /* ********************************************************** */
  // IP_literal | IPv4address | reg_name
  public static boolean host(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "host")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, HOST, "<host>");
    r = IP_literal(b, l + 1);
    if (!r) r = IPv4address(b, l + 1);
    if (!r) r = reg_name(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // long_option_name (EQUAL? option_value)?
  public static boolean long_option(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "long_option")) return false;
    if (!nextTokenIs(b, LONG_OPTION_IDENTIFIER)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, LONG_OPTION, null);
    r = long_option_name(b, l + 1);
    p = r; // pin = 1
    r = r && long_option_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (EQUAL? option_value)?
  private static boolean long_option_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "long_option_1")) return false;
    long_option_1_0(b, l + 1);
    return true;
  }

  // EQUAL? option_value
  private static boolean long_option_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "long_option_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = long_option_1_0_0(b, l + 1);
    r = r && option_value(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // EQUAL?
  private static boolean long_option_1_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "long_option_1_0_0")) return false;
    consumeToken(b, EQUAL);
    return true;
  }

  /* ********************************************************** */
  // LONG_OPTION_IDENTIFIER
  public static boolean long_option_name(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "long_option_name")) return false;
    if (!nextTokenIs(b, LONG_OPTION_IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LONG_OPTION_IDENTIFIER);
    exit_section_(b, m, LONG_OPTION_NAME, r);
    return r;
  }

  /* ********************************************************** */
  // h16_colon h16 | IPv4address
  static boolean ls32(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ls32")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = ls32_0(b, l + 1);
    if (!r) r = IPv4address(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // h16_colon h16
  private static boolean ls32_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ls32_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = h16_colon(b, l + 1);
    r = r && h16(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // marker_or
  static boolean marker(PsiBuilder b, int l) {
    return marker_or(b, l + 1);
  }

  /* ********************************************************** */
  // marker_expr (AND marker_expr)*
  public static boolean marker_and(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "marker_and")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, MARKER_AND, "<marker and>");
    r = marker_expr(b, l + 1);
    p = r; // pin = 1
    r = r && marker_and_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (AND marker_expr)*
  private static boolean marker_and_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "marker_and_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!marker_and_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "marker_and_1", c)) break;
    }
    return true;
  }

  // AND marker_expr
  private static boolean marker_and_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "marker_and_1_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, AND);
    p = r; // pin = 1
    r = r && marker_expr(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // (marker_name marker_op python_str)
  //                    | (LPARENTHESIS marker RPARENTHESIS)
  public static boolean marker_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "marker_expr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, MARKER_EXPR, "<marker expr>");
    r = marker_expr_0(b, l + 1);
    if (!r) r = marker_expr_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // marker_name marker_op python_str
  private static boolean marker_expr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "marker_expr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = marker_name(b, l + 1);
    r = r && marker_op(b, l + 1);
    r = r && python_str(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // LPARENTHESIS marker RPARENTHESIS
  private static boolean marker_expr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "marker_expr_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPARENTHESIS);
    r = r && marker(b, l + 1);
    r = r && consumeToken(b, RPARENTHESIS);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // ENV_MARKER_NAME | python_str
  public static boolean marker_name(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "marker_name")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, MARKER_NAME, "<marker name>");
    r = consumeToken(b, ENV_MARKER_NAME);
    if (!r) r = python_str(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // version_cmp | IN_OP | NOTIN_OP
  public static boolean marker_op(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "marker_op")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, MARKER_OP, "<marker op>");
    r = version_cmp(b, l + 1);
    if (!r) r = consumeToken(b, IN_OP);
    if (!r) r = consumeToken(b, NOTIN_OP);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // marker_and (OR marker_and)*
  public static boolean marker_or(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "marker_or")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, MARKER_OR, "<marker or>");
    r = marker_and(b, l + 1);
    p = r; // pin = 1
    r = r && marker_or_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (OR marker_and)*
  private static boolean marker_or_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "marker_or_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!marker_or_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "marker_or_1", c)) break;
    }
    return true;
  }

  // OR marker_and
  private static boolean marker_or_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "marker_or_1_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, OR);
    p = r; // pin = 1
    r = r && marker_and(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // editable_option? package_name extras? versionspec? long_option* quoted_marker?
  public static boolean name_req(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "name_req")) return false;
    if (!nextTokenIs(b, "<name req>", EDITABLE_OPTION_IDENTIFIER, PACKAGE_NAME_TOKEN)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, NAME_REQ, "<name req>");
    r = name_req_0(b, l + 1);
    r = r && package_name(b, l + 1);
    r = r && name_req_2(b, l + 1);
    r = r && name_req_3(b, l + 1);
    r = r && name_req_4(b, l + 1);
    r = r && name_req_5(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // editable_option?
  private static boolean name_req_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "name_req_0")) return false;
    editable_option(b, l + 1);
    return true;
  }

  // extras?
  private static boolean name_req_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "name_req_2")) return false;
    extras(b, l + 1);
    return true;
  }

  // versionspec?
  private static boolean name_req_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "name_req_3")) return false;
    versionspec(b, l + 1);
    return true;
  }

  // long_option*
  private static boolean name_req_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "name_req_4")) return false;
    while (true) {
      int c = current_position_(b);
      if (!long_option(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "name_req_4", c)) break;
    }
    return true;
  }

  // quoted_marker?
  private static boolean name_req_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "name_req_5")) return false;
    quoted_marker(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // !"0" DIGIT
  static boolean nz(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nz")) return false;
    if (!nextTokenIs(b, DIGIT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = nz_0(b, l + 1);
    r = r && consumeToken(b, DIGIT);
    exit_section_(b, m, null, r);
    return r;
  }

  // !"0"
  private static boolean nz_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nz_0")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !consumeToken(b, "0");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // long_option | short_option
  public static boolean option(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option")) return false;
    if (!nextTokenIs(b, "<option>", LONG_OPTION_IDENTIFIER, SHORT_OPTION_IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, OPTION, "<option>");
    r = long_option(b, l + 1);
    if (!r) r = short_option(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // OPTION_VALUE_TOKEN
  public static boolean option_value(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option_value")) return false;
    if (!nextTokenIs(b, OPTION_VALUE_TOKEN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, OPTION_VALUE_TOKEN);
    exit_section_(b, m, OPTION_VALUE, r);
    return r;
  }

  /* ********************************************************** */
  // PACKAGE_NAME_TOKEN
  public static boolean package_name(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "package_name")) return false;
    if (!nextTokenIs(b, PACKAGE_NAME_TOKEN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, PACKAGE_NAME_TOKEN);
    exit_section_(b, m, PACKAGE_NAME, r);
    return r;
  }

  /* ********************************************************** */
  // win_abs_path | abs_path | rel_path
  public static boolean path(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PATH, "<path>");
    r = win_abs_path(b, l + 1);
    if (!r) r = abs_path(b, l + 1);
    if (!r) r = rel_path(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // editable_option? path extras? quoted_marker?
  public static boolean path_req(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_req")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, PATH_REQ, "<path req>");
    r = path_req_0(b, l + 1);
    r = r && path(b, l + 1);
    p = r; // pin = 2
    r = r && report_error_(b, path_req_2(b, l + 1));
    r = p && path_req_3(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // editable_option?
  private static boolean path_req_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_req_0")) return false;
    editable_option(b, l + 1);
    return true;
  }

  // extras?
  private static boolean path_req_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_req_2")) return false;
    extras(b, l + 1);
    return true;
  }

  // quoted_marker?
  private static boolean path_req_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_req_3")) return false;
    quoted_marker(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // DIGIT | DOT | URI_UNRESERVED | PCT_ENCODED | URI_SUB_DELIMITER | COLON | AT | SLASH
  static boolean pchar(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pchar")) return false;
    boolean r;
    r = consumeToken(b, DIGIT);
    if (!r) r = consumeToken(b, DOT);
    if (!r) r = consumeToken(b, URI_UNRESERVED);
    if (!r) r = consumeToken(b, PCT_ENCODED);
    if (!r) r = consumeToken(b, URI_SUB_DELIMITER);
    if (!r) r = consumeToken(b, COLON);
    if (!r) r = consumeToken(b, AT);
    if (!r) r = consumeToken(b, SLASH);
    return r;
  }

  /* ********************************************************** */
  // DIGIT+
  public static boolean port(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "port")) return false;
    if (!nextTokenIs(b, DIGIT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, DIGIT);
    while (r) {
      int c = current_position_(b);
      if (!consumeToken(b, DIGIT)) break;
      if (!empty_element_parsed_guard_(b, "port", c)) break;
    }
    exit_section_(b, m, PORT, r);
    return r;
  }

  /* ********************************************************** */
  // DQUOTE QUOTED_STRING_TOKEN DQUOTE
  static boolean python_dquote_str(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "python_dquote_str")) return false;
    if (!nextTokenIs(b, DQUOTE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeTokens(b, 1, DQUOTE, QUOTED_STRING_TOKEN, DQUOTE);
    p = r; // pin = 1
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // SQUOTE QUOTED_STRING_TOKEN SQUOTE
  static boolean python_squote_str(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "python_squote_str")) return false;
    if (!nextTokenIs(b, SQUOTE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeTokens(b, 1, SQUOTE, QUOTED_STRING_TOKEN, SQUOTE);
    p = r; // pin = 1
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // python_dquote_str | python_squote_str
  public static boolean python_str(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "python_str")) return false;
    if (!nextTokenIs(b, "<python str>", DQUOTE, SQUOTE)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PYTHON_STR, "<python str>");
    r = python_dquote_str(b, l + 1);
    if (!r) r = python_squote_str(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // (pchar | QUESTION_MARK)*
  public static boolean query(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "query")) return false;
    Marker m = enter_section_(b, l, _NONE_, QUERY, "<query>");
    while (true) {
      int c = current_position_(b);
      if (!query_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "query", c)) break;
    }
    exit_section_(b, l, m, true, false, null);
    return true;
  }

  // pchar | QUESTION_MARK
  private static boolean query_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "query_0")) return false;
    boolean r;
    r = pchar(b, l + 1);
    if (!r) r = consumeToken(b, QUESTION_MARK);
    return r;
  }

  /* ********************************************************** */
  // SEMICOLON marker
  public static boolean quoted_marker(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "quoted_marker")) return false;
    if (!nextTokenIs(b, SEMICOLON)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, QUOTED_MARKER, null);
    r = consumeToken(b, SEMICOLON);
    p = r; // pin = 1
    r = r && marker(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // (URI_UNRESERVED | PCT_ENCODED | URI_SUB_DELIMITER | DOT | DIGIT)*
  static boolean reg_name(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "reg_name")) return false;
    while (true) {
      int c = current_position_(b);
      if (!reg_name_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "reg_name", c)) break;
    }
    return true;
  }

  // URI_UNRESERVED | PCT_ENCODED | URI_SUB_DELIMITER | DOT | DIGIT
  private static boolean reg_name_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "reg_name_0")) return false;
    boolean r;
    r = consumeToken(b, URI_UNRESERVED);
    if (!r) r = consumeToken(b, PCT_ENCODED);
    if (!r) r = consumeToken(b, URI_SUB_DELIMITER);
    if (!r) r = consumeToken(b, DOT);
    if (!r) r = consumeToken(b, DIGIT);
    return r;
  }

  /* ********************************************************** */
  // PATH_SEGMENT abs_path?
  static boolean rel_path(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "rel_path")) return false;
    if (!nextTokenIs(b, PATH_SEGMENT)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, PATH_SEGMENT);
    p = r; // pin = 1
    r = r && rel_path_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // abs_path?
  private static boolean rel_path_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "rel_path_1")) return false;
    abs_path(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // (specification | COMMENT | EOL)*
  static boolean requirementsFile(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "requirementsFile")) return false;
    while (true) {
      int c = current_position_(b);
      if (!requirementsFile_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "requirementsFile", c)) break;
    }
    return true;
  }

  // specification | COMMENT | EOL
  private static boolean requirementsFile_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "requirementsFile_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = specification(b, l + 1);
    if (!r) r = consumeToken(b, COMMENT);
    if (!r) r = consumeToken(b, EOL);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // URI_SCHEME
  public static boolean scheme(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "scheme")) return false;
    if (!nextTokenIs(b, URI_SCHEME)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, URI_SCHEME);
    exit_section_(b, m, SCHEME, r);
    return r;
  }

  /* ********************************************************** */
  // short_option_name option_value
  public static boolean short_option(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "short_option")) return false;
    if (!nextTokenIs(b, SHORT_OPTION_IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = short_option_name(b, l + 1);
    r = r && option_value(b, l + 1);
    exit_section_(b, m, SHORT_OPTION, r);
    return r;
  }

  /* ********************************************************** */
  // SHORT_OPTION_IDENTIFIER
  public static boolean short_option_name(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "short_option_name")) return false;
    if (!nextTokenIs(b, SHORT_OPTION_IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SHORT_OPTION_IDENTIFIER);
    exit_section_(b, m, SHORT_OPTION_NAME, r);
    return r;
  }

  /* ********************************************************** */
  // url_req
  //     | name_req
  //     | uri_reference
  //     | path_req
  //     | option
  static boolean specification(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "specification")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_);
    r = url_req(b, l + 1);
    if (!r) r = name_req(b, l + 1);
    if (!r) r = uri_reference(b, l + 1);
    if (!r) r = path_req(b, l + 1);
    if (!r) r = option(b, l + 1);
    exit_section_(b, l, m, r, false, RequirementsParser::statement_recover);
    return r;
  }

  /* ********************************************************** */
  // !EOL
  static boolean statement_recover(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statement_recover")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !consumeToken(b, EOL);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // scheme COLON SLASH SLASH authority uri_path? (QUESTION_MARK query)? (SHARP fragment)?
  public static boolean uri(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "uri")) return false;
    if (!nextTokenIs(b, URI_SCHEME)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = scheme(b, l + 1);
    r = r && consumeTokens(b, 0, COLON, SLASH, SLASH);
    r = r && authority(b, l + 1);
    r = r && uri_5(b, l + 1);
    r = r && uri_6(b, l + 1);
    r = r && uri_7(b, l + 1);
    exit_section_(b, m, URI, r);
    return r;
  }

  // uri_path?
  private static boolean uri_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "uri_5")) return false;
    uri_path(b, l + 1);
    return true;
  }

  // (QUESTION_MARK query)?
  private static boolean uri_6(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "uri_6")) return false;
    uri_6_0(b, l + 1);
    return true;
  }

  // QUESTION_MARK query
  private static boolean uri_6_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "uri_6_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, QUESTION_MARK);
    r = r && query(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (SHARP fragment)?
  private static boolean uri_7(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "uri_7")) return false;
    uri_7_0(b, l + 1);
    return true;
  }

  // SHARP fragment
  private static boolean uri_7_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "uri_7_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SHARP);
    r = r && fragment(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // pchar+
  public static boolean uri_path(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "uri_path")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, URI_PATH, "<uri path>");
    r = pchar(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!pchar(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "uri_path", c)) break;
    }
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // editable_option? any_uri quoted_marker?
  public static boolean uri_reference(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "uri_reference")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, URI_REFERENCE, "<uri reference>");
    r = uri_reference_0(b, l + 1);
    r = r && any_uri(b, l + 1);
    p = r; // pin = 2
    r = r && uri_reference_2(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // editable_option?
  private static boolean uri_reference_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "uri_reference_0")) return false;
    editable_option(b, l + 1);
    return true;
  }

  // quoted_marker?
  private static boolean uri_reference_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "uri_reference_2")) return false;
    quoted_marker(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // editable_option? package_name extras? AT any_uri quoted_marker?
  public static boolean url_req(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "url_req")) return false;
    if (!nextTokenIs(b, "<url req>", EDITABLE_OPTION_IDENTIFIER, PACKAGE_NAME_TOKEN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, URL_REQ, "<url req>");
    r = url_req_0(b, l + 1);
    r = r && package_name(b, l + 1);
    r = r && url_req_2(b, l + 1);
    r = r && consumeToken(b, AT);
    p = r; // pin = 4
    r = r && report_error_(b, any_uri(b, l + 1));
    r = p && url_req_5(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // editable_option?
  private static boolean url_req_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "url_req_0")) return false;
    editable_option(b, l + 1);
    return true;
  }

  // extras?
  private static boolean url_req_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "url_req_2")) return false;
    extras(b, l + 1);
    return true;
  }

  // quoted_marker?
  private static boolean url_req_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "url_req_5")) return false;
    quoted_marker(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // (URI_UNRESERVED | PCT_ENCODED | env_variable | URI_SUB_DELIMITER | DOLLAR_SIGN | COLON)*
  public static boolean userinfo(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "userinfo")) return false;
    Marker m = enter_section_(b, l, _NONE_, USERINFO, "<userinfo>");
    while (true) {
      int c = current_position_(b);
      if (!userinfo_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "userinfo", c)) break;
    }
    exit_section_(b, l, m, true, false, null);
    return true;
  }

  // URI_UNRESERVED | PCT_ENCODED | env_variable | URI_SUB_DELIMITER | DOLLAR_SIGN | COLON
  private static boolean userinfo_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "userinfo_0")) return false;
    boolean r;
    r = consumeToken(b, URI_UNRESERVED);
    if (!r) r = consumeToken(b, PCT_ENCODED);
    if (!r) r = env_variable(b, l + 1);
    if (!r) r = consumeToken(b, URI_SUB_DELIMITER);
    if (!r) r = consumeToken(b, DOLLAR_SIGN);
    if (!r) r = consumeToken(b, COLON);
    return r;
  }

  /* ********************************************************** */
  // ENV_VARIABLE_NAME
  public static boolean variable_name(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "variable_name")) return false;
    if (!nextTokenIs(b, ENV_VARIABLE_NAME)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ENV_VARIABLE_NAME);
    exit_section_(b, m, VARIABLE_NAME, r);
    return r;
  }

  /* ********************************************************** */
  // (LETTER | DIGIT | UNDERSCORE | HYPHEN | DOT | URI_UNRESERVED | URI_SUB_DELIMITER | SLASH)+
  public static boolean vcs_revision(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "vcs_revision")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, VCS_REVISION, "<vcs revision>");
    r = vcs_revision_0(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!vcs_revision_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "vcs_revision", c)) break;
    }
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // LETTER | DIGIT | UNDERSCORE | HYPHEN | DOT | URI_UNRESERVED | URI_SUB_DELIMITER | SLASH
  private static boolean vcs_revision_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "vcs_revision_0")) return false;
    boolean r;
    r = consumeToken(b, LETTER);
    if (!r) r = consumeToken(b, DIGIT);
    if (!r) r = consumeToken(b, UNDERSCORE);
    if (!r) r = consumeToken(b, HYPHEN);
    if (!r) r = consumeToken(b, DOT);
    if (!r) r = consumeToken(b, URI_UNRESERVED);
    if (!r) r = consumeToken(b, URI_SUB_DELIMITER);
    if (!r) r = consumeToken(b, SLASH);
    return r;
  }

  /* ********************************************************** */
  // VERSION_TOKEN
  public static boolean version(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "version")) return false;
    if (!nextTokenIs(b, VERSION_TOKEN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, VERSION_TOKEN);
    exit_section_(b, m, VERSION, r);
    return r;
  }

  /* ********************************************************** */
  // VERSION_CMP_TOKEN
  public static boolean version_cmp(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "version_cmp")) return false;
    if (!nextTokenIs(b, VERSION_CMP_TOKEN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, VERSION_CMP_TOKEN);
    exit_section_(b, m, VERSION_CMP, r);
    return r;
  }

  /* ********************************************************** */
  // version_one (COMMA version_one)*
  static boolean version_many(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "version_many")) return false;
    if (!nextTokenIs(b, VERSION_CMP_TOKEN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = version_one(b, l + 1);
    r = r && version_many_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMA version_one)*
  private static boolean version_many_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "version_many_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!version_many_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "version_many_1", c)) break;
    }
    return true;
  }

  // COMMA version_one
  private static boolean version_many_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "version_many_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && version_one(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // version_cmp version
  public static boolean version_one(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "version_one")) return false;
    if (!nextTokenIs(b, VERSION_CMP_TOKEN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, VERSION_ONE, null);
    r = version_cmp(b, l + 1);
    p = r; // pin = 1
    r = r && version(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // LPARENTHESIS version_many RPARENTHESIS | version_many
  public static boolean versionspec(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "versionspec")) return false;
    if (!nextTokenIs(b, "<versionspec>", LPARENTHESIS, VERSION_CMP_TOKEN)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, VERSIONSPEC, "<versionspec>");
    r = versionspec_0(b, l + 1);
    if (!r) r = version_many(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // LPARENTHESIS version_many RPARENTHESIS
  private static boolean versionspec_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "versionspec_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPARENTHESIS);
    r = r && version_many(b, l + 1);
    r = r && consumeToken(b, RPARENTHESIS);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // DRIVE_LETTER abs_path
  static boolean win_abs_path(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "win_abs_path")) return false;
    if (!nextTokenIs(b, DRIVE_LETTER)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, DRIVE_LETTER);
    p = r; // pin = 1
    r = r && abs_path(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

}
