// This is a generated file. Not intended for manual editing.
package de.plushnikov.intellij.plugin.language.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static de.plushnikov.intellij.plugin.language.psi.LombokConfigTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class LombokConfigParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType root_, PsiBuilder builder_) {
    parseLight(root_, builder_);
    return builder_.getTreeBuilt();
  }

  public void parseLight(IElementType root_, PsiBuilder builder_) {
    boolean result_;
    builder_ = adapt_builder_(root_, builder_, this, null);
    Marker marker_ = enter_section_(builder_, 0, _COLLAPSE_, null);
    result_ = parse_root_(root_, builder_);
    exit_section_(builder_, 0, marker_, root_, result_, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType root_, PsiBuilder builder_) {
    return parse_root_(root_, builder_, 0);
  }

  static boolean parse_root_(IElementType root_, PsiBuilder builder_, int level_) {
    return simpleFile(builder_, level_ + 1);
  }

  /* ********************************************************** */
  // CLEAR KEY
  public static boolean cleaner(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "cleaner")) return false;
    if (!nextTokenIs(builder_, CLEAR)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, CLEAR, KEY);
    exit_section_(builder_, marker_, CLEANER, result_);
    return result_;
  }

  /* ********************************************************** */
  // property|cleaner|COMMENT|CRLF
  static boolean item_(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "item_")) return false;
    boolean result_;
    result_ = property(builder_, level_ + 1);
    if (!result_) result_ = cleaner(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, COMMENT);
    if (!result_) result_ = consumeToken(builder_, CRLF);
    return result_;
  }

  /* ********************************************************** */
  // SIGN? SEPARATOR
  public static boolean operation(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "operation")) return false;
    if (!nextTokenIs(builder_, "<operation>", SEPARATOR, SIGN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, OPERATION, "<operation>");
    result_ = operation_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, SEPARATOR);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // SIGN?
  private static boolean operation_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "operation_0")) return false;
    consumeToken(builder_, SIGN);
    return true;
  }

  /* ********************************************************** */
  // KEY operation VALUE
  public static boolean property(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "property")) return false;
    if (!nextTokenIs(builder_, KEY)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, KEY);
    result_ = result_ && operation(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, VALUE);
    exit_section_(builder_, marker_, PROPERTY, result_);
    return result_;
  }

  /* ********************************************************** */
  // item_*
  static boolean simpleFile(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "simpleFile")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!item_(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "simpleFile", pos_)) break;
    }
    return true;
  }

}
