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
    if (root_ == CONSOLE_COMMAND) {
      result_ = consoleCommand(builder_, 0);
    }
    else if (root_ == CONTEXT_HUNK) {
      result_ = contextHunk(builder_, 0);
    }
    else if (root_ == CONTEXT_HUNK_FROM) {
      result_ = contextHunkFrom(builder_, 0);
    }
    else if (root_ == CONTEXT_HUNK_TO) {
      result_ = contextHunkTo(builder_, 0);
    }
    else if (root_ == NORMAL_HUNK) {
      result_ = normalHunk(builder_, 0);
    }
    else if (root_ == UNIFIED_HUNK) {
      result_ = unifiedHunk(builder_, 0);
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
  // WHITE_SPACE | OTHER
  static boolean anyLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "anyLine")) return false;
    if (!nextTokenIs(builder_, "", OTHER, WHITE_SPACE)) return false;
    boolean result_;
    result_ = consumeToken(builder_, WHITE_SPACE);
    if (!result_) result_ = consumeToken(builder_, OTHER);
    return result_;
  }

  /* ********************************************************** */
  // COMMAND
  public static boolean consoleCommand(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "consoleCommand")) return false;
    if (!nextTokenIs(builder_, COMMAND)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMAND);
    exit_section_(builder_, marker_, CONSOLE_COMMAND, result_);
    return result_;
  }

  /* ********************************************************** */
  // CONTEXT_FROM_LABEL CONTEXT_TO_LABEL contextHunk+
  static boolean contextDiff(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "contextDiff")) return false;
    if (!nextTokenIs(builder_, CONTEXT_FROM_LABEL)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, CONTEXT_FROM_LABEL, CONTEXT_TO_LABEL);
    result_ = result_ && contextDiff_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // contextHunk+
  private static boolean contextDiff_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "contextDiff_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = contextHunk(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!contextHunk(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "contextDiff_2", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // CONTEXT_COMMON_LINE | CONTEXT_CHANGED_LINE | CONTEXT_DELETED_LINE
  static boolean contextFromFileLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "contextFromFileLine")) return false;
    boolean result_;
    result_ = consumeToken(builder_, CONTEXT_COMMON_LINE);
    if (!result_) result_ = consumeToken(builder_, CONTEXT_CHANGED_LINE);
    if (!result_) result_ = consumeToken(builder_, CONTEXT_DELETED_LINE);
    return result_;
  }

  /* ********************************************************** */
  // CONTEXT_HUNK_SEPARATOR contextHunkFrom contextHunkTo
  public static boolean contextHunk(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "contextHunk")) return false;
    if (!nextTokenIs(builder_, CONTEXT_HUNK_SEPARATOR)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CONTEXT_HUNK_SEPARATOR);
    result_ = result_ && contextHunkFrom(builder_, level_ + 1);
    result_ = result_ && contextHunkTo(builder_, level_ + 1);
    exit_section_(builder_, marker_, CONTEXT_HUNK, result_);
    return result_;
  }

  /* ********************************************************** */
  // CONTEXT_FROM_LINE_NUMBERS contextFromFileLine* (EOL_HINT)?
  public static boolean contextHunkFrom(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "contextHunkFrom")) return false;
    if (!nextTokenIs(builder_, CONTEXT_FROM_LINE_NUMBERS)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CONTEXT_FROM_LINE_NUMBERS);
    result_ = result_ && contextHunkFrom_1(builder_, level_ + 1);
    result_ = result_ && contextHunkFrom_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, CONTEXT_HUNK_FROM, result_);
    return result_;
  }

  // contextFromFileLine*
  private static boolean contextHunkFrom_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "contextHunkFrom_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!contextFromFileLine(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "contextHunkFrom_1", pos_)) break;
    }
    return true;
  }

  // (EOL_HINT)?
  private static boolean contextHunkFrom_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "contextHunkFrom_2")) return false;
    consumeToken(builder_, EOL_HINT);
    return true;
  }

  /* ********************************************************** */
  // CONTEXT_TO_LINE_NUMBERS contextToFileLine* (EOL_HINT)?
  public static boolean contextHunkTo(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "contextHunkTo")) return false;
    if (!nextTokenIs(builder_, CONTEXT_TO_LINE_NUMBERS)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CONTEXT_TO_LINE_NUMBERS);
    result_ = result_ && contextHunkTo_1(builder_, level_ + 1);
    result_ = result_ && contextHunkTo_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, CONTEXT_HUNK_TO, result_);
    return result_;
  }

  // contextToFileLine*
  private static boolean contextHunkTo_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "contextHunkTo_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!contextToFileLine(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "contextHunkTo_1", pos_)) break;
    }
    return true;
  }

  // (EOL_HINT)?
  private static boolean contextHunkTo_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "contextHunkTo_2")) return false;
    consumeToken(builder_, EOL_HINT);
    return true;
  }

  /* ********************************************************** */
  // CONTEXT_COMMON_LINE | CONTEXT_CHANGED_LINE | CONTEXT_INSERTED_LINE
  static boolean contextToFileLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "contextToFileLine")) return false;
    boolean result_;
    result_ = consumeToken(builder_, CONTEXT_COMMON_LINE);
    if (!result_) result_ = consumeToken(builder_, CONTEXT_CHANGED_LINE);
    if (!result_) result_ = consumeToken(builder_, CONTEXT_INSERTED_LINE);
    return result_;
  }

  /* ********************************************************** */
  // (leadingText (normalDiff | contextDiff | unifiedDiff))+ trailingText
  static boolean diffFile(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "diffFile")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = diffFile_0(builder_, level_ + 1);
    result_ = result_ && trailingText(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (leadingText (normalDiff | contextDiff | unifiedDiff))+
  private static boolean diffFile_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "diffFile_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = diffFile_0_0(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!diffFile_0_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "diffFile_0", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // leadingText (normalDiff | contextDiff | unifiedDiff)
  private static boolean diffFile_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "diffFile_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = leadingText(builder_, level_ + 1);
    result_ = result_ && diffFile_0_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // normalDiff | contextDiff | unifiedDiff
  private static boolean diffFile_0_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "diffFile_0_0_1")) return false;
    boolean result_;
    result_ = normalDiff(builder_, level_ + 1);
    if (!result_) result_ = contextDiff(builder_, level_ + 1);
    if (!result_) result_ = unifiedDiff(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // anyLine* (consoleCommand anyLine*)?
  static boolean leadingText(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "leadingText")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = leadingText_0(builder_, level_ + 1);
    result_ = result_ && leadingText_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // anyLine*
  private static boolean leadingText_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "leadingText_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!anyLine(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "leadingText_0", pos_)) break;
    }
    return true;
  }

  // (consoleCommand anyLine*)?
  private static boolean leadingText_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "leadingText_1")) return false;
    leadingText_1_0(builder_, level_ + 1);
    return true;
  }

  // consoleCommand anyLine*
  private static boolean leadingText_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "leadingText_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consoleCommand(builder_, level_ + 1);
    result_ = result_ && leadingText_1_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // anyLine*
  private static boolean leadingText_1_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "leadingText_1_0_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!anyLine(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "leadingText_1_0_1", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // normalHunk+
  static boolean normalDiff(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "normalDiff")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = normalHunk(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!normalHunk(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "normalDiff", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // normalHunkAdd | normalHunkChange | normalHunkDelete
  public static boolean normalHunk(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "normalHunk")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, NORMAL_HUNK, "<normal hunk>");
    result_ = normalHunkAdd(builder_, level_ + 1);
    if (!result_) result_ = normalHunkChange(builder_, level_ + 1);
    if (!result_) result_ = normalHunkDelete(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // NORMAL_ADD_COMMAND NORMAL_TO_LINE+ EOL_HINT?
  static boolean normalHunkAdd(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "normalHunkAdd")) return false;
    if (!nextTokenIs(builder_, NORMAL_ADD_COMMAND)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NORMAL_ADD_COMMAND);
    result_ = result_ && normalHunkAdd_1(builder_, level_ + 1);
    result_ = result_ && normalHunkAdd_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // NORMAL_TO_LINE+
  private static boolean normalHunkAdd_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "normalHunkAdd_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NORMAL_TO_LINE);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, NORMAL_TO_LINE)) break;
      if (!empty_element_parsed_guard_(builder_, "normalHunkAdd_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL_HINT?
  private static boolean normalHunkAdd_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "normalHunkAdd_2")) return false;
    consumeToken(builder_, EOL_HINT);
    return true;
  }

  /* ********************************************************** */
  // NORMAL_CHANGE_COMMAND NORMAL_FROM_LINE+ EOL_HINT? NORMAL_SEPARATOR NORMAL_TO_LINE+ EOL_HINT?
  static boolean normalHunkChange(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "normalHunkChange")) return false;
    if (!nextTokenIs(builder_, NORMAL_CHANGE_COMMAND)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NORMAL_CHANGE_COMMAND);
    result_ = result_ && normalHunkChange_1(builder_, level_ + 1);
    result_ = result_ && normalHunkChange_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, NORMAL_SEPARATOR);
    result_ = result_ && normalHunkChange_4(builder_, level_ + 1);
    result_ = result_ && normalHunkChange_5(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // NORMAL_FROM_LINE+
  private static boolean normalHunkChange_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "normalHunkChange_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NORMAL_FROM_LINE);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, NORMAL_FROM_LINE)) break;
      if (!empty_element_parsed_guard_(builder_, "normalHunkChange_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL_HINT?
  private static boolean normalHunkChange_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "normalHunkChange_2")) return false;
    consumeToken(builder_, EOL_HINT);
    return true;
  }

  // NORMAL_TO_LINE+
  private static boolean normalHunkChange_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "normalHunkChange_4")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NORMAL_TO_LINE);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, NORMAL_TO_LINE)) break;
      if (!empty_element_parsed_guard_(builder_, "normalHunkChange_4", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL_HINT?
  private static boolean normalHunkChange_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "normalHunkChange_5")) return false;
    consumeToken(builder_, EOL_HINT);
    return true;
  }

  /* ********************************************************** */
  // NORMAL_DELETE_COMMAND NORMAL_FROM_LINE+ EOL_HINT?
  static boolean normalHunkDelete(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "normalHunkDelete")) return false;
    if (!nextTokenIs(builder_, NORMAL_DELETE_COMMAND)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NORMAL_DELETE_COMMAND);
    result_ = result_ && normalHunkDelete_1(builder_, level_ + 1);
    result_ = result_ && normalHunkDelete_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // NORMAL_FROM_LINE+
  private static boolean normalHunkDelete_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "normalHunkDelete_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NORMAL_FROM_LINE);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, NORMAL_FROM_LINE)) break;
      if (!empty_element_parsed_guard_(builder_, "normalHunkDelete_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EOL_HINT?
  private static boolean normalHunkDelete_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "normalHunkDelete_2")) return false;
    consumeToken(builder_, EOL_HINT);
    return true;
  }

  /* ********************************************************** */
  // anyLine*
  static boolean trailingText(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "trailingText")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!anyLine(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "trailingText", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // UNIFIED_FROM_LABEL UNIFIED_TO_LABEL unifiedHunk+
  static boolean unifiedDiff(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "unifiedDiff")) return false;
    if (!nextTokenIs(builder_, UNIFIED_FROM_LABEL)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, UNIFIED_FROM_LABEL, UNIFIED_TO_LABEL);
    result_ = result_ && unifiedDiff_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // unifiedHunk+
  private static boolean unifiedDiff_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "unifiedDiff_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = unifiedHunk(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!unifiedHunk(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "unifiedDiff_2", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // UNIFIED_LINE_NUMBERS (unifiedLine | WHITE_SPACE)+
  public static boolean unifiedHunk(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "unifiedHunk")) return false;
    if (!nextTokenIs(builder_, UNIFIED_LINE_NUMBERS)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, UNIFIED_LINE_NUMBERS);
    result_ = result_ && unifiedHunk_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, UNIFIED_HUNK, result_);
    return result_;
  }

  // (unifiedLine | WHITE_SPACE)+
  private static boolean unifiedHunk_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "unifiedHunk_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = unifiedHunk_1_0(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!unifiedHunk_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "unifiedHunk_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // unifiedLine | WHITE_SPACE
  private static boolean unifiedHunk_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "unifiedHunk_1_0")) return false;
    boolean result_;
    result_ = unifiedLine(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, WHITE_SPACE);
    return result_;
  }

  /* ********************************************************** */
  // UNIFIED_INSERTED_LINE | UNIFIED_DELETED_LINE | UNIFIED_COMMON_LINE | EOL_HINT
  static boolean unifiedLine(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "unifiedLine")) return false;
    boolean result_;
    result_ = consumeToken(builder_, UNIFIED_INSERTED_LINE);
    if (!result_) result_ = consumeToken(builder_, UNIFIED_DELETED_LINE);
    if (!result_) result_ = consumeToken(builder_, UNIFIED_COMMON_LINE);
    if (!result_) result_ = consumeToken(builder_, EOL_HINT);
    return result_;
  }

}
