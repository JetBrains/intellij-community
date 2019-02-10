// This is a generated file. Not intended for manual editing.
package de.thomasrosenau.diffplugin.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static de.thomasrosenau.diffplugin.psi.DiffTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class DiffParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType root_, PsiBuilder builder_) {
    parseLight(root_, builder_);
    return builder_.getTreeBuilt();
  }

  public void parseLight(IElementType root_, PsiBuilder builder_) {
    boolean result_;
    builder_ = adapt_builder_(root_, builder_, this, null);
    Marker marker_ = enter_section_(builder_, 0, _COLLAPSE_, null);
    if (root_ == LINE) {
      result_ = line(builder_, 0);
    }
    else {
      result_ = parse_root_(root_, builder_, 0);
    }
    exit_section_(builder_, 0, marker_, root_, result_, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType root_, PsiBuilder builder_, int level_) {
    return diffFile(builder_, level_ + 1);
  }

  /* ********************************************************** */
  // line*
  static boolean diffFile(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "diffFile")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!line(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "diffFile", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // COMMAND | FILE | ADDED | DELETED | MODIFIED | SEPARATOR | HUNK_HEAD | EOLHINT | OTHER
  public static boolean line(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "line")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, LINE, "<line>");
    result_ = consumeToken(builder_, COMMAND);
    if (!result_) result_ = consumeToken(builder_, FILE);
    if (!result_) result_ = consumeToken(builder_, ADDED);
    if (!result_) result_ = consumeToken(builder_, DELETED);
    if (!result_) result_ = consumeToken(builder_, MODIFIED);
    if (!result_) result_ = consumeToken(builder_, SEPARATOR);
    if (!result_) result_ = consumeToken(builder_, HUNK_HEAD);
    if (!result_) result_ = consumeToken(builder_, EOLHINT);
    if (!result_) result_ = consumeToken(builder_, OTHER);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

}
