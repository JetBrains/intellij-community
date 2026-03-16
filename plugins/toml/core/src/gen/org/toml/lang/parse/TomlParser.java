// This is a generated file. Not intended for manual editing.
package org.toml.lang.parse;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static org.toml.lang.psi.TomlElementTypes.*;
import static org.toml.lang.parse.TomlParserUtil.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class TomlParser implements PsiParser, LightPsiParser {

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
    return File(b, l + 1);
  }

  /* ********************************************************** */
  // '[' ArrayElement* ']'
  public static boolean Array(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Array")) return false;
    if (!nextTokenIs(b, L_BRACKET)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, ARRAY, null);
    r = consumeToken(b, L_BRACKET);
    p = r; // pin = 1
    r = r && report_error_(b, Array_1(b, l + 1));
    r = p && consumeToken(b, R_BRACKET) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // ArrayElement*
  private static boolean Array_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Array_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!ArrayElement(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "Array_1", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // !']' Value (','|&']')
  static boolean ArrayElement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ArrayElement")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = ArrayElement_0(b, l + 1);
    p = r; // pin = 1
    r = r && report_error_(b, Value(b, l + 1));
    r = p && ArrayElement_2(b, l + 1) && r;
    exit_section_(b, l, m, r, p, TomlParser::ArrayElement_recover);
    return r || p;
  }

  // !']'
  private static boolean ArrayElement_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ArrayElement_0")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !consumeToken(b, R_BRACKET);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // ','|&']'
  private static boolean ArrayElement_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ArrayElement_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    if (!r) r = ArrayElement_2_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // &']'
  private static boolean ArrayElement_2_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ArrayElement_2_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = consumeToken(b, R_BRACKET);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // !(']' | Value_first)
  static boolean ArrayElement_recover(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ArrayElement_recover")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !ArrayElement_recover_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // ']' | Value_first
  private static boolean ArrayElement_recover_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ArrayElement_recover_0")) return false;
    boolean r;
    r = consumeTokenFast(b, R_BRACKET);
    if (!r) r = Value_first(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // ArrayTableHeader NewLineKeyValue*
  public static boolean ArrayTable(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ArrayTable")) return false;
    if (!nextTokenIs(b, L_BRACKET)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = ArrayTableHeader(b, l + 1);
    r = r && ArrayTable_1(b, l + 1);
    exit_section_(b, m, ARRAY_TABLE, r);
    return r;
  }

  // NewLineKeyValue*
  private static boolean ArrayTable_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ArrayTable_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!NewLineKeyValue(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "ArrayTable_1", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // '[''[' Key ']'']'
  public static boolean ArrayTableHeader(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ArrayTableHeader")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, TABLE_HEADER, "<array table header>");
    r = consumeTokens(b, 2, L_BRACKET, L_BRACKET);
    p = r; // pin = 2
    r = r && report_error_(b, Key(b, l + 1));
    r = p && report_error_(b, consumeTokens(b, -1, R_BRACKET, R_BRACKET)) && r;
    exit_section_(b, l, m, r, p, TomlParser::EatUntilNextLine_recover);
    return r || p;
  }

  /* ********************************************************** */
  // BARE_KEY
  //   | <<remap 'BARE_KEY_OR_NUMBER' 'BARE_KEY'>>
  //   | <<remap 'BARE_KEY_OR_DATE' 'BARE_KEY'>>
  //   | <<remap 'BARE_KEY_OR_BOOLEAN' 'BARE_KEY'>>
  static boolean BareKey(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "BareKey")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, BARE_KEY);
    if (!r) r = remap(b, l + 1, BARE_KEY_OR_NUMBER, BARE_KEY);
    if (!r) r = remap(b, l + 1, BARE_KEY_OR_DATE, BARE_KEY);
    if (!r) r = remap(b, l + 1, BARE_KEY_OR_BOOLEAN, BARE_KEY);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // <<remap 'BARE_KEY_OR_BOOLEAN' 'BOOLEAN'>> | BOOLEAN
  static boolean Boolean(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Boolean")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = remap(b, l + 1, BARE_KEY_OR_BOOLEAN, BOOLEAN);
    if (!r) r = consumeToken(b, BOOLEAN);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // <<remap 'BARE_KEY_OR_DATE' 'DATE_TIME'>> | DATE_TIME
  static boolean Date(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Date")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = remap(b, l + 1, BARE_KEY_OR_DATE, DATE_TIME);
    if (!r) r = consumeToken(b, DATE_TIME);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // !<<atNewLine <<any>>>>
  static boolean EatUntilNextLine_recover(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "EatUntilNextLine_recover")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !atNewLine(b, l + 1, EatUntilNextLine_recover_0_0_parser_);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // FileForm*
  static boolean File(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "File")) return false;
    while (true) {
      int c = current_position_(b);
      if (!FileForm(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "File", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // NewLineKeyValue | ArrayTable | Table
  static boolean FileForm(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "FileForm")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_);
    r = NewLineKeyValue(b, l + 1);
    if (!r) r = ArrayTable(b, l + 1);
    if (!r) r = Table(b, l + 1);
    exit_section_(b, l, m, r, false, TomlParser::FileForm_recover);
    return r;
  }

  /* ********************************************************** */
  // !<<atNewLine (BARE_KEY | BARE_KEY_OR_NUMBER | BARE_KEY_OR_DATE | BARE_KEY_OR_BOOLEAN | BASIC_STRING | LITERAL_STRING | '[')>>
  static boolean FileForm_recover(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "FileForm_recover")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !atNewLine(b, l + 1, TomlParser::FileForm_recover_0_0);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // BARE_KEY | BARE_KEY_OR_NUMBER | BARE_KEY_OR_DATE | BARE_KEY_OR_BOOLEAN | BASIC_STRING | LITERAL_STRING | '['
  private static boolean FileForm_recover_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "FileForm_recover_0_0")) return false;
    boolean r;
    r = consumeToken(b, BARE_KEY);
    if (!r) r = consumeToken(b, BARE_KEY_OR_NUMBER);
    if (!r) r = consumeToken(b, BARE_KEY_OR_DATE);
    if (!r) r = consumeToken(b, BARE_KEY_OR_BOOLEAN);
    if (!r) r = consumeToken(b, BASIC_STRING);
    if (!r) r = consumeToken(b, LITERAL_STRING);
    if (!r) r = consumeToken(b, L_BRACKET);
    return r;
  }

  /* ********************************************************** */
  // '{' InlineTableElement* '}'
  public static boolean InlineTable(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "InlineTable")) return false;
    if (!nextTokenIs(b, L_CURLY)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, INLINE_TABLE, null);
    r = consumeToken(b, L_CURLY);
    p = r; // pin = 1
    r = r && report_error_(b, InlineTable_1(b, l + 1));
    r = p && consumeToken(b, R_CURLY) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // InlineTableElement*
  private static boolean InlineTable_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "InlineTable_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!InlineTableElement(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "InlineTable_1", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // KeyValue (','|&'}')
  static boolean InlineTableElement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "InlineTableElement")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = KeyValue(b, l + 1);
    p = r; // pin = 1
    r = r && InlineTableElement_1(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // ','|&'}'
  private static boolean InlineTableElement_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "InlineTableElement_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    if (!r) r = InlineTableElement_1_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // &'}'
  private static boolean InlineTableElement_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "InlineTableElement_1_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = consumeToken(b, R_CURLY);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // KeySegment ('.' KeySegment)*
  public static boolean Key(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Key")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, KEY, "<key>");
    r = KeySegment(b, l + 1);
    r = r && Key_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // ('.' KeySegment)*
  private static boolean Key_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Key_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!Key_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "Key_1", c)) break;
    }
    return true;
  }

  // '.' KeySegment
  private static boolean Key_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Key_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, DOT);
    r = r && KeySegment(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // BareKey | BASIC_STRING | LITERAL_STRING
  public static boolean KeySegment(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "KeySegment")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, KEY_SEGMENT, "<key segment>");
    r = BareKey(b, l + 1);
    if (!r) r = consumeToken(b, BASIC_STRING);
    if (!r) r = consumeToken(b, LITERAL_STRING);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // Key '=' <<atSameLine Value>>
  public static boolean KeyValue(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "KeyValue")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, KEY_VALUE, "<key value>");
    r = Key(b, l + 1);
    p = r; // pin = 1
    r = r && report_error_(b, consumeToken(b, EQ));
    r = p && atSameLine(b, l + 1, TomlParser::Value) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // Number | Date | Boolean
  //   | BASIC_STRING | LITERAL_STRING
  //   | MULTILINE_BASIC_STRING | MULTILINE_LITERAL_STRING
  public static boolean Literal(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Literal")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, LITERAL, "<literal>");
    r = Number(b, l + 1);
    if (!r) r = Date(b, l + 1);
    if (!r) r = Boolean(b, l + 1);
    if (!r) r = consumeToken(b, BASIC_STRING);
    if (!r) r = consumeToken(b, LITERAL_STRING);
    if (!r) r = consumeToken(b, MULTILINE_BASIC_STRING);
    if (!r) r = consumeToken(b, MULTILINE_LITERAL_STRING);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // <<atNewLine KeyValue>>
  public static boolean NewLineKeyValue(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "NewLineKeyValue")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, KEY_VALUE, "<new line key value>");
    r = atNewLine(b, l + 1, TomlParser::KeyValue);
    exit_section_(b, l, m, r, false, TomlParser::EatUntilNextLine_recover);
    return r;
  }

  /* ********************************************************** */
  // <<remap 'BARE_KEY_OR_NUMBER' 'NUMBER'>> | NUMBER
  static boolean Number(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Number")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = remap(b, l + 1, BARE_KEY_OR_NUMBER, NUMBER);
    if (!r) r = consumeToken(b, NUMBER);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // TableHeader NewLineKeyValue*
  public static boolean Table(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Table")) return false;
    if (!nextTokenIs(b, L_BRACKET)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = TableHeader(b, l + 1);
    r = r && Table_1(b, l + 1);
    exit_section_(b, m, TABLE, r);
    return r;
  }

  // NewLineKeyValue*
  private static boolean Table_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Table_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!NewLineKeyValue(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "Table_1", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // '[' Key ']'
  public static boolean TableHeader(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "TableHeader")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, TABLE_HEADER, "<table header>");
    r = consumeToken(b, L_BRACKET);
    p = r; // pin = 1
    r = r && report_error_(b, Key(b, l + 1));
    r = p && consumeToken(b, R_BRACKET) && r;
    exit_section_(b, l, m, r, p, TomlParser::EatUntilNextLine_recover);
    return r || p;
  }

  /* ********************************************************** */
  // Literal | Array | InlineTable
  static boolean Value(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Value")) return false;
    boolean r;
    r = Literal(b, l + 1);
    if (!r) r = Array(b, l + 1);
    if (!r) r = InlineTable(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // BARE_KEY_OR_NUMBER | NUMBER | BARE_KEY_OR_DATE | DATE_TIME
  //                       | BARE_KEY_OR_BOOLEAN | BOOLEAN
  //                       | BASIC_STRING | LITERAL_STRING
  //                       | MULTILINE_BASIC_STRING | MULTILINE_LITERAL_STRING
  //                       | '[' | '{'
  static boolean Value_first(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Value_first")) return false;
    boolean r;
    r = consumeToken(b, BARE_KEY_OR_NUMBER);
    if (!r) r = consumeToken(b, NUMBER);
    if (!r) r = consumeToken(b, BARE_KEY_OR_DATE);
    if (!r) r = consumeToken(b, DATE_TIME);
    if (!r) r = consumeToken(b, BARE_KEY_OR_BOOLEAN);
    if (!r) r = consumeToken(b, BOOLEAN);
    if (!r) r = consumeToken(b, BASIC_STRING);
    if (!r) r = consumeToken(b, LITERAL_STRING);
    if (!r) r = consumeToken(b, MULTILINE_BASIC_STRING);
    if (!r) r = consumeToken(b, MULTILINE_LITERAL_STRING);
    if (!r) r = consumeToken(b, L_BRACKET);
    if (!r) r = consumeToken(b, L_CURLY);
    return r;
  }

  static final Parser EatUntilNextLine_recover_0_0_parser_ = (b, l) -> any(b, l + 1);
}
