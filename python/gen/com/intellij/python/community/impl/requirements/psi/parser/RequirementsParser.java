// This is a generated file. Not intended for manual editing.
package com.intellij.python.community.impl.requirements.psi.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.intellij.python.community.impl.requirements.psi.RequirementsTypes.*;
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
  public static boolean IP_literal(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IP_literal")) return false;
    if (!nextTokenIs(b, LSBRACE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LSBRACE);
    r = r && IP_literal_1(b, l + 1);
    r = r && consumeToken(b, RSBRACE);
    exit_section_(b, m, IP_LITERAL, r);
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
  public static boolean IPv4address(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv4address")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, I_PV_4_ADDRESS, "<i pv 4 address>");
    r = dec_octet(b, l + 1);
    r = r && consumeToken(b, DOT);
    r = r && dec_octet(b, l + 1);
    r = r && consumeToken(b, DOT);
    r = r && dec_octet(b, l + 1);
    r = r && consumeToken(b, DOT);
    r = r && dec_octet(b, l + 1);
    exit_section_(b, l, m, r, false, null);
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
  public static boolean IPv6address(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPv6address")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, I_PV_6_ADDRESS, "<i pv 6 address>");
    r = IPv6address_0(b, l + 1);
    if (!r) r = IPv6address_1(b, l + 1);
    if (!r) r = IPv6address_2(b, l + 1);
    if (!r) r = IPv6address_3(b, l + 1);
    if (!r) r = IPv6address_4(b, l + 1);
    if (!r) r = IPv6address_5(b, l + 1);
    if (!r) r = IPv6address_6(b, l + 1);
    if (!r) r = IPv6address_7(b, l + 1);
    if (!r) r = IPv6address_8(b, l + 1);
    exit_section_(b, l, m, r, false, null);
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
  // 'v' hexdig+ DOT (unreserved | SUB_DELIMS | DOLLAR_SIGN | COLON)+
  public static boolean IPvFuture(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPvFuture")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, I_PV_FUTURE, "<i pv future>");
    r = consumeToken(b, "v");
    r = r && IPvFuture_1(b, l + 1);
    r = r && consumeToken(b, DOT);
    r = r && IPvFuture_3(b, l + 1);
    exit_section_(b, l, m, r, false, null);
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

  // (unreserved | SUB_DELIMS | DOLLAR_SIGN | COLON)+
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

  // unreserved | SUB_DELIMS | DOLLAR_SIGN | COLON
  private static boolean IPvFuture_3_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "IPvFuture_3_0")) return false;
    boolean r;
    r = unreserved(b, l + 1);
    if (!r) r = consumeToken(b, SUB_DELIMS);
    if (!r) r = consumeToken(b, DOLLAR_SIGN);
    if (!r) r = consumeToken(b, COLON);
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
  // extras_list | BINARY_ALL | BINARY_NONE
  public static boolean binary_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "binary_list")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, BINARY_LIST, "<binary list>");
    r = extras_list(b, l + 1);
    if (!r) r = consumeToken(b, BINARY_ALL);
    if (!r) r = consumeToken(b, BINARY_NONE);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // CONSTRAINT  uri_reference
  public static boolean constraint_req(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "constraint_req")) return false;
    if (!nextTokenIs(b, CONSTRAINT)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, CONSTRAINT_REQ, null);
    r = consumeToken(b, CONSTRAINT);
    p = r; // pin = 1
    r = r && uri_reference(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // DIGIT // 0-9
  //                   | nz DIGIT // 10-99
  //                   | "1" DIGIT DIGIT // 100-199
  //                   | "2" ("0" | "1" | "2" | "3" | "4") DIGIT // 200-249
  //                   | "2" "5" ("0" | "1" | "2" | "3" | "4" | "5")
  public static boolean dec_octet(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dec_octet")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, DEC_OCTET, "<dec octet>");
    r = consumeToken(b, DIGIT);
    if (!r) r = dec_octet_1(b, l + 1);
    if (!r) r = dec_octet_2(b, l + 1);
    if (!r) r = dec_octet_3(b, l + 1);
    if (!r) r = dec_octet_4(b, l + 1);
    exit_section_(b, l, m, r, false, null);
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
  // EDITABLE uri_reference
  public static boolean editable_req(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "editable_req")) return false;
    if (!nextTokenIs(b, EDITABLE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, EDITABLE_REQ, null);
    r = consumeToken(b, EDITABLE);
    p = r; // pin = 1
    r = r && uri_reference(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // DOLLAR_SIGN LBRACE variable_name RBRACE
  public static boolean env_variable(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "env_variable")) return false;
    if (!nextTokenIs(b, DOLLAR_SIGN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, DOLLAR_SIGN, LBRACE);
    r = r && variable_name(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, m, ENV_VARIABLE, r);
    return r;
  }

  /* ********************************************************** */
  // EXTRA_INDEX_URL uri_reference
  public static boolean extra_index_url_req(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "extra_index_url_req")) return false;
    if (!nextTokenIs(b, EXTRA_INDEX_URL)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, EXTRA_INDEX_URL_REQ, null);
    r = consumeToken(b, EXTRA_INDEX_URL);
    p = r; // pin = 1
    r = r && uri_reference(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // LSBRACE extras_list? RSBRACE
  public static boolean extras(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "extras")) return false;
    if (!nextTokenIs(b, LSBRACE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, EXTRAS, null);
    r = consumeToken(b, LSBRACE);
    p = r; // pin = 1
    r = r && report_error_(b, extras_1(b, l + 1));
    r = p && consumeToken(b, RSBRACE) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // extras_list?
  private static boolean extras_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "extras_1")) return false;
    extras_list(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // IDENTIFIER (COMMA IDENTIFIER)*
  public static boolean extras_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "extras_list")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, EXTRAS_LIST, null);
    r = consumeToken(b, IDENTIFIER);
    p = r; // pin = 1
    r = r && extras_list_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (COMMA IDENTIFIER)*
  private static boolean extras_list_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "extras_list_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!extras_list_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "extras_list_1", c)) break;
    }
    return true;
  }

  // COMMA IDENTIFIER
  private static boolean extras_list_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "extras_list_1_0")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeTokens(b, 1, COMMA, IDENTIFIER);
    p = r; // pin = 1
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // FIND_LINKS uri_reference
  public static boolean find_links_req(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "find_links_req")) return false;
    if (!nextTokenIs(b, FIND_LINKS)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, FIND_LINKS_REQ, null);
    r = consumeToken(b, FIND_LINKS);
    p = r; // pin = 1
    r = r && uri_reference(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // (pchar | SLASH | QUESTION_MARK)*
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

  // pchar | SLASH | QUESTION_MARK
  private static boolean fragment_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fragment_0")) return false;
    boolean r;
    r = pchar(b, l + 1);
    if (!r) r = consumeToken(b, SLASH);
    if (!r) r = consumeToken(b, QUESTION_MARK);
    return r;
  }

  /* ********************************************************** */
  // hexdig hexdig? hexdig? hexdig?
  public static boolean h16(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "h16")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, H_16, "<h 16>");
    r = hexdig(b, l + 1);
    r = r && h16_1(b, l + 1);
    r = r && h16_2(b, l + 1);
    r = r && h16_3(b, l + 1);
    exit_section_(b, l, m, r, false, null);
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
  public static boolean h16_colon(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "h16_colon")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, H_16_COLON, "<h 16 colon>");
    r = h16(b, l + 1);
    r = r && consumeToken(b, COLON);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // HASH EQUAL IDENTIFIER COLON HEX
  public static boolean hash_option(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "hash_option")) return false;
    if (!nextTokenIs(b, HASH)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, HASH_OPTION, null);
    r = consumeTokens(b, 1, HASH, EQUAL, IDENTIFIER, COLON, HEX);
    p = r; // pin = 1
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // DIGIT | 'a' | 'A' | 'b' | 'B' | 'c' | 'C' | 'd' | 'D' | 'e' | 'E' | 'f' | 'F'
  public static boolean hexdig(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "hexdig")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, HEXDIG, "<hexdig>");
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
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // SLASH SLASH authority path_abempty | path_absolute | path_rootless | path_empty
  public static boolean hier_part(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "hier_part")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, HIER_PART, "<hier part>");
    r = hier_part_0(b, l + 1);
    if (!r) r = path_absolute(b, l + 1);
    if (!r) r = path_rootless(b, l + 1);
    if (!r) r = path_empty(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // SLASH SLASH authority path_abempty
  private static boolean hier_part_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "hier_part_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, SLASH, SLASH);
    r = r && authority(b, l + 1);
    r = r && path_abempty(b, l + 1);
    exit_section_(b, m, null, r);
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
  // INDEX_URL uri_reference
  public static boolean index_url_req(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "index_url_req")) return false;
    if (!nextTokenIs(b, INDEX_URL)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, INDEX_URL_REQ, null);
    r = consumeToken(b, INDEX_URL);
    p = r; // pin = 1
    r = r && uri_reference(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // (specification COMMENT? (EOL | <<eof>>)) | EOL
  static boolean line_(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "line_")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = line__0(b, l + 1);
    if (!r) r = consumeToken(b, EOL);
    exit_section_(b, m, null, r);
    return r;
  }

  // specification COMMENT? (EOL | <<eof>>)
  private static boolean line__0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "line__0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = specification(b, l + 1);
    r = r && line__0_1(b, l + 1);
    r = r && line__0_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // COMMENT?
  private static boolean line__0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "line__0_1")) return false;
    consumeToken(b, COMMENT);
    return true;
  }

  // EOL | <<eof>>
  private static boolean line__0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "line__0_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, EOL);
    if (!r) r = eof(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // h16_colon h16 | IPv4address
  public static boolean ls32(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ls32")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, LS_32, "<ls 32>");
    r = ls32_0(b, l + 1);
    if (!r) r = IPv4address(b, l + 1);
    exit_section_(b, l, m, r, false, null);
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
  // marker_var marker_op marker_var
  //                   | LPARENTHESIS marker RPARENTHESIS
  public static boolean marker_expr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "marker_expr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, MARKER_EXPR, "<marker expr>");
    r = marker_expr_0(b, l + 1);
    if (!r) r = marker_expr_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // marker_var marker_op marker_var
  private static boolean marker_expr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "marker_expr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = marker_var(b, l + 1);
    r = r && marker_op(b, l + 1);
    r = r && marker_var(b, l + 1);
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
  // VERSION_CMP | IN | NOT IN
  public static boolean marker_op(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "marker_op")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, MARKER_OP, "<marker op>");
    r = consumeToken(b, VERSION_CMP);
    if (!r) r = consumeToken(b, IN);
    if (!r) r = parseTokens(b, 0, NOT, IN);
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
  // ENV_VAR | python_str
  public static boolean marker_var(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "marker_var")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, MARKER_VAR, "<marker var>");
    r = consumeToken(b, ENV_VAR);
    if (!r) r = python_str(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // simple_name extras? !AT versionspec?
  //                    (LONG_OPTION hash_option)* quoted_marker?
  public static boolean name_req(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "name_req")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, NAME_REQ, null);
    r = simple_name(b, l + 1);
    r = r && name_req_1(b, l + 1);
    r = r && name_req_2(b, l + 1);
    r = r && name_req_3(b, l + 1);
    r = r && name_req_4(b, l + 1);
    p = r; // pin = 5
    r = r && name_req_5(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // extras?
  private static boolean name_req_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "name_req_1")) return false;
    extras(b, l + 1);
    return true;
  }

  // !AT
  private static boolean name_req_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "name_req_2")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !consumeToken(b, AT);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // versionspec?
  private static boolean name_req_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "name_req_3")) return false;
    versionspec(b, l + 1);
    return true;
  }

  // (LONG_OPTION hash_option)*
  private static boolean name_req_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "name_req_4")) return false;
    while (true) {
      int c = current_position_(b);
      if (!name_req_4_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "name_req_4", c)) break;
    }
    return true;
  }

  // LONG_OPTION hash_option
  private static boolean name_req_4_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "name_req_4_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LONG_OPTION);
    r = r && hash_option(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // quoted_marker?
  private static boolean name_req_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "name_req_5")) return false;
    quoted_marker(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // NO_BINARY binary_list
  public static boolean no_binary_req(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "no_binary_req")) return false;
    if (!nextTokenIs(b, NO_BINARY)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, NO_BINARY_REQ, null);
    r = consumeToken(b, NO_BINARY);
    p = r; // pin = 1
    r = r && binary_list(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // NO_INDEX
  public static boolean no_index_req(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "no_index_req")) return false;
    if (!nextTokenIs(b, NO_INDEX)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, NO_INDEX);
    exit_section_(b, m, NO_INDEX_REQ, r);
    return r;
  }

  /* ********************************************************** */
  // !"0" DIGIT
  public static boolean nz(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nz")) return false;
    if (!nextTokenIs(b, DIGIT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = nz_0(b, l + 1);
    r = r && consumeToken(b, DIGIT);
    exit_section_(b, m, NZ, r);
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
  // ONLY_BINARY binary_list
  public static boolean only_binary_req(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "only_binary_req")) return false;
    if (!nextTokenIs(b, ONLY_BINARY)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, ONLY_BINARY_REQ, null);
    r = consumeToken(b, ONLY_BINARY);
    p = r; // pin = 1
    r = r && binary_list(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // (SHORT_OPTION | LONG_OPTION) (
  //                    refer_req
  //                    | constraint_req
  //                    | editable_req
  //                    | index_url_req
  //                    | extra_index_url_req
  //                    | no_index_req
  //                    | find_links_req
  //                    | no_binary_req
  //                    | only_binary_req
  //                    | prefer_binary_req
  //                    | require_hashes_req
  //                    | pre_req
  //                    | trusted_host_req
  //                    | use_feature_req)
  public static boolean option(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option")) return false;
    if (!nextTokenIs(b, "<option>", LONG_OPTION, SHORT_OPTION)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, OPTION, "<option>");
    r = option_0(b, l + 1);
    p = r; // pin = 1
    r = r && option_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // SHORT_OPTION | LONG_OPTION
  private static boolean option_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option_0")) return false;
    boolean r;
    r = consumeToken(b, SHORT_OPTION);
    if (!r) r = consumeToken(b, LONG_OPTION);
    return r;
  }

  // refer_req
  //                    | constraint_req
  //                    | editable_req
  //                    | index_url_req
  //                    | extra_index_url_req
  //                    | no_index_req
  //                    | find_links_req
  //                    | no_binary_req
  //                    | only_binary_req
  //                    | prefer_binary_req
  //                    | require_hashes_req
  //                    | pre_req
  //                    | trusted_host_req
  //                    | use_feature_req
  private static boolean option_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option_1")) return false;
    boolean r;
    r = refer_req(b, l + 1);
    if (!r) r = constraint_req(b, l + 1);
    if (!r) r = editable_req(b, l + 1);
    if (!r) r = index_url_req(b, l + 1);
    if (!r) r = extra_index_url_req(b, l + 1);
    if (!r) r = no_index_req(b, l + 1);
    if (!r) r = find_links_req(b, l + 1);
    if (!r) r = no_binary_req(b, l + 1);
    if (!r) r = only_binary_req(b, l + 1);
    if (!r) r = prefer_binary_req(b, l + 1);
    if (!r) r = require_hashes_req(b, l + 1);
    if (!r) r = pre_req(b, l + 1);
    if (!r) r = trusted_host_req(b, l + 1);
    if (!r) r = use_feature_req(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // (SLASH segment)*
  public static boolean path_abempty(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_abempty")) return false;
    Marker m = enter_section_(b, l, _NONE_, PATH_ABEMPTY, "<path abempty>");
    while (true) {
      int c = current_position_(b);
      if (!path_abempty_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "path_abempty", c)) break;
    }
    exit_section_(b, l, m, true, false, null);
    return true;
  }

  // SLASH segment
  private static boolean path_abempty_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_abempty_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SLASH);
    r = r && segment(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // SLASH (segment_nz (SLASH segment)*)?
  public static boolean path_absolute(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_absolute")) return false;
    if (!nextTokenIs(b, SLASH)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SLASH);
    r = r && path_absolute_1(b, l + 1);
    exit_section_(b, m, PATH_ABSOLUTE, r);
    return r;
  }

  // (segment_nz (SLASH segment)*)?
  private static boolean path_absolute_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_absolute_1")) return false;
    path_absolute_1_0(b, l + 1);
    return true;
  }

  // segment_nz (SLASH segment)*
  private static boolean path_absolute_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_absolute_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = segment_nz(b, l + 1);
    r = r && path_absolute_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (SLASH segment)*
  private static boolean path_absolute_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_absolute_1_0_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!path_absolute_1_0_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "path_absolute_1_0_1", c)) break;
    }
    return true;
  }

  // SLASH segment
  private static boolean path_absolute_1_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_absolute_1_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SLASH);
    r = r && segment(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // pchar{0}
  public static boolean path_empty(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_empty")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PATH_EMPTY, "<path empty>");
    r = pchar(b, l + 1);
    r = r && path_empty_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // {0}
  private static boolean path_empty_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_empty_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, "0");
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // segment_nz_nc (SLASH segment)*
  public static boolean path_noscheme(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_noscheme")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PATH_NOSCHEME, "<path noscheme>");
    r = segment_nz_nc(b, l + 1);
    r = r && path_noscheme_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (SLASH segment)*
  private static boolean path_noscheme_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_noscheme_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!path_noscheme_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "path_noscheme_1", c)) break;
    }
    return true;
  }

  // SLASH segment
  private static boolean path_noscheme_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_noscheme_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SLASH);
    r = r && segment(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // uri_reference quoted_marker?
  public static boolean path_req(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_req")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, PATH_REQ, "<path req>");
    r = uri_reference(b, l + 1);
    p = r; // pin = 1
    r = r && path_req_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // quoted_marker?
  private static boolean path_req_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_req_1")) return false;
    quoted_marker(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // segment_nz (SLASH segment)*
  public static boolean path_rootless(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_rootless")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PATH_ROOTLESS, "<path rootless>");
    r = segment_nz(b, l + 1);
    r = r && path_rootless_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (SLASH segment)*
  private static boolean path_rootless_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_rootless_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!path_rootless_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "path_rootless_1", c)) break;
    }
    return true;
  }

  // SLASH segment
  private static boolean path_rootless_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "path_rootless_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SLASH);
    r = r && segment(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // unreserved | pct_encoded | SUB_DELIMS | DOLLAR_SIGN | COLON | AT | PLUS
  public static boolean pchar(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pchar")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PCHAR, "<pchar>");
    r = unreserved(b, l + 1);
    if (!r) r = pct_encoded(b, l + 1);
    if (!r) r = consumeToken(b, SUB_DELIMS);
    if (!r) r = consumeToken(b, DOLLAR_SIGN);
    if (!r) r = consumeToken(b, COLON);
    if (!r) r = consumeToken(b, AT);
    if (!r) r = consumeToken(b, PLUS);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // PERCENT_SIGN hexdig
  public static boolean pct_encoded(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pct_encoded")) return false;
    if (!nextTokenIs(b, PERCENT_SIGN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, PERCENT_SIGN);
    r = r && hexdig(b, l + 1);
    exit_section_(b, m, PCT_ENCODED, r);
    return r;
  }

  /* ********************************************************** */
  // DIGIT*
  public static boolean port(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "port")) return false;
    Marker m = enter_section_(b, l, _NONE_, PORT, "<port>");
    while (true) {
      int c = current_position_(b);
      if (!consumeToken(b, DIGIT)) break;
      if (!empty_element_parsed_guard_(b, "port", c)) break;
    }
    exit_section_(b, l, m, true, false, null);
    return true;
  }

  /* ********************************************************** */
  // PRE
  public static boolean pre_req(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pre_req")) return false;
    if (!nextTokenIs(b, PRE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, PRE);
    exit_section_(b, m, PRE_REQ, r);
    return r;
  }

  /* ********************************************************** */
  // PREFER_BINARY
  public static boolean prefer_binary_req(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "prefer_binary_req")) return false;
    if (!nextTokenIs(b, PREFER_BINARY)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, PREFER_BINARY);
    exit_section_(b, m, PREFER_BINARY_REQ, r);
    return r;
  }

  /* ********************************************************** */
  // DQUOTE (PYTHON_STR_C)? DQUOTE
  static boolean python_dquote_str(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "python_dquote_str")) return false;
    if (!nextTokenIs(b, DQUOTE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, DQUOTE);
    p = r; // pin = 1
    r = r && report_error_(b, python_dquote_str_1(b, l + 1));
    r = p && consumeToken(b, DQUOTE) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (PYTHON_STR_C)?
  private static boolean python_dquote_str_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "python_dquote_str_1")) return false;
    consumeToken(b, PYTHON_STR_C);
    return true;
  }

  /* ********************************************************** */
  // SQUOTE (PYTHON_STR_C)? SQUOTE
  static boolean python_squote_str(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "python_squote_str")) return false;
    if (!nextTokenIs(b, SQUOTE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, SQUOTE);
    p = r; // pin = 1
    r = r && report_error_(b, python_squote_str_1(b, l + 1));
    r = p && consumeToken(b, SQUOTE) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (PYTHON_STR_C)?
  private static boolean python_squote_str_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "python_squote_str_1")) return false;
    consumeToken(b, PYTHON_STR_C);
    return true;
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
  // (pchar | SLASH | QUESTION_MARK)*
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

  // pchar | SLASH | QUESTION_MARK
  private static boolean query_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "query_0")) return false;
    boolean r;
    r = pchar(b, l + 1);
    if (!r) r = consumeToken(b, SLASH);
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
  // REFER uri_reference
  public static boolean refer_req(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "refer_req")) return false;
    if (!nextTokenIs(b, REFER)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, REFER_REQ, null);
    r = consumeToken(b, REFER);
    p = r; // pin = 1
    r = r && uri_reference(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // (unreserved | pct_encoded | SUB_DELIMS | DOLLAR_SIGN)*
  public static boolean reg_name(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "reg_name")) return false;
    Marker m = enter_section_(b, l, _NONE_, REG_NAME, "<reg name>");
    while (true) {
      int c = current_position_(b);
      if (!reg_name_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "reg_name", c)) break;
    }
    exit_section_(b, l, m, true, false, null);
    return true;
  }

  // unreserved | pct_encoded | SUB_DELIMS | DOLLAR_SIGN
  private static boolean reg_name_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "reg_name_0")) return false;
    boolean r;
    r = unreserved(b, l + 1);
    if (!r) r = pct_encoded(b, l + 1);
    if (!r) r = consumeToken(b, SUB_DELIMS);
    if (!r) r = consumeToken(b, DOLLAR_SIGN);
    return r;
  }

  /* ********************************************************** */
  // SLASH SLASH authority path_abempty | path_absolute | path_noscheme | path_empty
  public static boolean relative_part(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "relative_part")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, RELATIVE_PART, "<relative part>");
    r = relative_part_0(b, l + 1);
    if (!r) r = path_absolute(b, l + 1);
    if (!r) r = path_noscheme(b, l + 1);
    if (!r) r = path_empty(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // SLASH SLASH authority path_abempty
  private static boolean relative_part_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "relative_part_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, SLASH, SLASH);
    r = r && authority(b, l + 1);
    r = r && path_abempty(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // relative_part (QUESTION_MARK query)? (SHARP fragment)?
  public static boolean relative_ref(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "relative_ref")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, RELATIVE_REF, "<relative ref>");
    r = relative_part(b, l + 1);
    r = r && relative_ref_1(b, l + 1);
    r = r && relative_ref_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (QUESTION_MARK query)?
  private static boolean relative_ref_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "relative_ref_1")) return false;
    relative_ref_1_0(b, l + 1);
    return true;
  }

  // QUESTION_MARK query
  private static boolean relative_ref_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "relative_ref_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, QUESTION_MARK);
    r = r && query(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (SHARP fragment)?
  private static boolean relative_ref_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "relative_ref_2")) return false;
    relative_ref_2_0(b, l + 1);
    return true;
  }

  // SHARP fragment
  private static boolean relative_ref_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "relative_ref_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SHARP);
    r = r && fragment(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // REQUIRE_HASHES
  public static boolean require_hashes_req(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "require_hashes_req")) return false;
    if (!nextTokenIs(b, REQUIRE_HASHES)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, REQUIRE_HASHES);
    exit_section_(b, m, REQUIRE_HASHES_REQ, r);
    return r;
  }

  /* ********************************************************** */
  // line_*
  static boolean requirementsFile(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "requirementsFile")) return false;
    while (true) {
      int c = current_position_(b);
      if (!line_(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "requirementsFile", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // LETTER (LETTER | DIGIT | PLUS | MINUS | DOT)*
  public static boolean scheme(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "scheme")) return false;
    if (!nextTokenIs(b, LETTER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LETTER);
    r = r && scheme_1(b, l + 1);
    exit_section_(b, m, SCHEME, r);
    return r;
  }

  // (LETTER | DIGIT | PLUS | MINUS | DOT)*
  private static boolean scheme_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "scheme_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!scheme_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "scheme_1", c)) break;
    }
    return true;
  }

  // LETTER | DIGIT | PLUS | MINUS | DOT
  private static boolean scheme_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "scheme_1_0")) return false;
    boolean r;
    r = consumeToken(b, LETTER);
    if (!r) r = consumeToken(b, DIGIT);
    if (!r) r = consumeToken(b, PLUS);
    if (!r) r = consumeToken(b, MINUS);
    if (!r) r = consumeToken(b, DOT);
    return r;
  }

  /* ********************************************************** */
  // pchar*
  public static boolean segment(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "segment")) return false;
    Marker m = enter_section_(b, l, _NONE_, SEGMENT, "<segment>");
    while (true) {
      int c = current_position_(b);
      if (!pchar(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "segment", c)) break;
    }
    exit_section_(b, l, m, true, false, null);
    return true;
  }

  /* ********************************************************** */
  // pchar+
  public static boolean segment_nz(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "segment_nz")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, SEGMENT_NZ, "<segment nz>");
    r = pchar(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!pchar(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "segment_nz", c)) break;
    }
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // (unreserved | pct_encoded | SUB_DELIMS | DOLLAR_SIGN | AT)+
  public static boolean segment_nz_nc(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "segment_nz_nc")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, SEGMENT_NZ_NC, "<segment nz nc>");
    r = segment_nz_nc_0(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!segment_nz_nc_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "segment_nz_nc", c)) break;
    }
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // unreserved | pct_encoded | SUB_DELIMS | DOLLAR_SIGN | AT
  private static boolean segment_nz_nc_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "segment_nz_nc_0")) return false;
    boolean r;
    r = unreserved(b, l + 1);
    if (!r) r = pct_encoded(b, l + 1);
    if (!r) r = consumeToken(b, SUB_DELIMS);
    if (!r) r = consumeToken(b, DOLLAR_SIGN);
    if (!r) r = consumeToken(b, AT);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER
  public static boolean simple_name(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simple_name")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    exit_section_(b, m, SIMPLE_NAME, r);
    return r;
  }

  /* ********************************************************** */
  // name_req
  //                           | option
  //                           | path_req
  //                           | url_req
  static boolean specification(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "specification")) return false;
    boolean r;
    r = name_req(b, l + 1);
    if (!r) r = option(b, l + 1);
    if (!r) r = path_req(b, l + 1);
    if (!r) r = url_req(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // TRUSTED_HOST host (COLON port)?
  public static boolean trusted_host_req(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "trusted_host_req")) return false;
    if (!nextTokenIs(b, TRUSTED_HOST)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, TRUSTED_HOST_REQ, null);
    r = consumeToken(b, TRUSTED_HOST);
    p = r; // pin = 1
    r = r && report_error_(b, host(b, l + 1));
    r = p && trusted_host_req_2(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // (COLON port)?
  private static boolean trusted_host_req_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "trusted_host_req_2")) return false;
    trusted_host_req_2_0(b, l + 1);
    return true;
  }

  // COLON port
  private static boolean trusted_host_req_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "trusted_host_req_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COLON);
    r = r && port(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // LETTER | DIGIT | MINUS | DOT | UNDERSCORE | TILDA
  public static boolean unreserved(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unreserved")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, UNRESERVED, "<unreserved>");
    r = consumeToken(b, LETTER);
    if (!r) r = consumeToken(b, DIGIT);
    if (!r) r = consumeToken(b, MINUS);
    if (!r) r = consumeToken(b, DOT);
    if (!r) r = consumeToken(b, UNDERSCORE);
    if (!r) r = consumeToken(b, TILDA);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // scheme (AT | COLON) hier_part (QUESTION_MARK query)? (SHARP fragment)?
  public static boolean uri(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "uri")) return false;
    if (!nextTokenIs(b, LETTER)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, URI, null);
    r = scheme(b, l + 1);
    r = r && uri_1(b, l + 1);
    p = r; // pin = 2
    r = r && report_error_(b, hier_part(b, l + 1));
    r = p && report_error_(b, uri_3(b, l + 1)) && r;
    r = p && uri_4(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // AT | COLON
  private static boolean uri_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "uri_1")) return false;
    boolean r;
    r = consumeToken(b, AT);
    if (!r) r = consumeToken(b, COLON);
    return r;
  }

  // (QUESTION_MARK query)?
  private static boolean uri_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "uri_3")) return false;
    uri_3_0(b, l + 1);
    return true;
  }

  // QUESTION_MARK query
  private static boolean uri_3_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "uri_3_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, QUESTION_MARK);
    r = r && query(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (SHARP fragment)?
  private static boolean uri_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "uri_4")) return false;
    uri_4_0(b, l + 1);
    return true;
  }

  // SHARP fragment
  private static boolean uri_4_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "uri_4_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SHARP);
    r = r && fragment(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // uri | relative_ref
  public static boolean uri_reference(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "uri_reference")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, URI_REFERENCE, "<uri reference>");
    r = uri(b, l + 1);
    if (!r) r = relative_ref(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // simple_name extras? urlspec quoted_marker?
  public static boolean url_req(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "url_req")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = simple_name(b, l + 1);
    r = r && url_req_1(b, l + 1);
    r = r && urlspec(b, l + 1);
    r = r && url_req_3(b, l + 1);
    exit_section_(b, m, URL_REQ, r);
    return r;
  }

  // extras?
  private static boolean url_req_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "url_req_1")) return false;
    extras(b, l + 1);
    return true;
  }

  // quoted_marker?
  private static boolean url_req_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "url_req_3")) return false;
    quoted_marker(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // AT uri_reference
  public static boolean urlspec(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "urlspec")) return false;
    if (!nextTokenIs(b, AT)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, URLSPEC, null);
    r = consumeToken(b, AT);
    p = r; // pin = 1
    r = r && uri_reference(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // USE_FEATURE IDENTIFIER
  public static boolean use_feature_req(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "use_feature_req")) return false;
    if (!nextTokenIs(b, USE_FEATURE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, USE_FEATURE_REQ, null);
    r = consumeTokens(b, 1, USE_FEATURE, IDENTIFIER);
    p = r; // pin = 1
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // (unreserved | pct_encoded | env_variable | SUB_DELIMS | DOLLAR_SIGN | COLON)*
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

  // unreserved | pct_encoded | env_variable | SUB_DELIMS | DOLLAR_SIGN | COLON
  private static boolean userinfo_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "userinfo_0")) return false;
    boolean r;
    r = unreserved(b, l + 1);
    if (!r) r = pct_encoded(b, l + 1);
    if (!r) r = env_variable(b, l + 1);
    if (!r) r = consumeToken(b, SUB_DELIMS);
    if (!r) r = consumeToken(b, DOLLAR_SIGN);
    if (!r) r = consumeToken(b, COLON);
    return r;
  }

  /* ********************************************************** */
  // LETTER (LETTER | DIGIT | UNDERSCORE)*
  public static boolean variable_name(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "variable_name")) return false;
    if (!nextTokenIs(b, LETTER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LETTER);
    r = r && variable_name_1(b, l + 1);
    exit_section_(b, m, VARIABLE_NAME, r);
    return r;
  }

  // (LETTER | DIGIT | UNDERSCORE)*
  private static boolean variable_name_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "variable_name_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!variable_name_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "variable_name_1", c)) break;
    }
    return true;
  }

  // LETTER | DIGIT | UNDERSCORE
  private static boolean variable_name_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "variable_name_1_0")) return false;
    boolean r;
    r = consumeToken(b, LETTER);
    if (!r) r = consumeToken(b, DIGIT);
    if (!r) r = consumeToken(b, UNDERSCORE);
    return r;
  }

  /* ********************************************************** */
  // VERSION_CMP
  public static boolean version_cmp_stmt(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "version_cmp_stmt")) return false;
    if (!nextTokenIs(b, VERSION_CMP)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, VERSION_CMP);
    exit_section_(b, m, VERSION_CMP_STMT, r);
    return r;
  }

  /* ********************************************************** */
  // version_one (COMMA version_one)*
  static boolean version_many(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "version_many")) return false;
    if (!nextTokenIs(b, VERSION_CMP)) return false;
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
  // version_cmp_stmt version_stmt
  public static boolean version_one(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "version_one")) return false;
    if (!nextTokenIs(b, VERSION_CMP)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = version_cmp_stmt(b, l + 1);
    r = r && version_stmt(b, l + 1);
    exit_section_(b, m, VERSION_ONE, r);
    return r;
  }

  /* ********************************************************** */
  // VERSION
  public static boolean version_stmt(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "version_stmt")) return false;
    if (!nextTokenIs(b, VERSION)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, VERSION);
    exit_section_(b, m, VERSION_STMT, r);
    return r;
  }

  /* ********************************************************** */
  // LPARENTHESIS version_many RPARENTHESIS | version_many
  public static boolean versionspec(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "versionspec")) return false;
    if (!nextTokenIs(b, "<versionspec>", LPARENTHESIS, VERSION_CMP)) return false;
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

}
