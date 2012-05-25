/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// This is a generated file. Not intended for manual editing.
package com.jetbrains.typoscript.lang;

import org.jetbrains.annotations.*;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import com.intellij.openapi.diagnostic.Logger;
import static com.jetbrains.typoscript.lang.TypoScriptElementTypes.*;
import static com.jetbrains.typoscript.lang.TypoScriptParserUtil.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import static com.jetbrains.typoscript.lang.TypoScriptTokenTypes.*;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class TypoScriptGeneratedParser implements PsiParser {

  public static Logger LOG_ = Logger.getInstance("com.jetbrains.typoscript.lang.TypoScriptGeneratedParser");

  @NotNull
  public ASTNode parse(final IElementType root_, final PsiBuilder builder_) {
    int level_ = 0;
    boolean result_;
    if (root_ == ASSIGNMENT) {
      result_ = assignment(builder_, level_ + 1);
    }
    else if (root_ == CODE_BLOCK) {
      result_ = code_block(builder_, level_ + 1);
    }
    else if (root_ == CONDITION_ELEMENT) {
      result_ = condition_element(builder_, level_ + 1);
    }
    else if (root_ == COPYING) {
      result_ = copying(builder_, level_ + 1);
    }
    else if (root_ == INCLUDE_STATEMENT_ELEMENT) {
      result_ = include_statement_element(builder_, level_ + 1);
    }
    else if (root_ == MULTILINE_VALUE_ASSIGNMENT) {
      result_ = multiline_value_assignment(builder_, level_ + 1);
    }
    else if (root_ == OBJECT_PATH) {
      result_ = object_path(builder_, level_ + 1);
    }
    else if (root_ == UNSETTING) {
      result_ = unsetting(builder_, level_ + 1);
    }
    else if (root_ == VALUE_MODIFICATION) {
      result_ = value_modification(builder_, level_ + 1);
    }
    else {
      Marker marker_ = builder_.mark();
      result_ = parse_root_(root_, builder_, level_);
      while (builder_.getTokenType() != null) {
        builder_.advanceLexer();
      }
      marker_.done(root_);
    }
    return builder_.getTreeBuilt();
  }

  protected boolean parse_root_(final IElementType root_, final PsiBuilder builder_, final int level_) {
    return file(builder_, level_ + 1);
  }

  /* ********************************************************** */
  // object_path '=' ASSIGNMENT_VALUE?
  public static boolean assignment(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "assignment")) return false;
    if (!nextTokenIs(builder_, OBJECT_PATH_ENTITY) && !nextTokenIs(builder_, OBJECT_PATH_SEPARATOR)) return false;
    boolean result_ = false;
    boolean pinned_ = false;
    final Marker marker_ = builder_.mark();
    enterErrorRecordingSection(builder_, level_, _SECTION_GENERAL_);
    result_ = object_path(builder_, level_ + 1);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, consumeToken(builder_, ASSIGNMENT_OPERATOR));
    result_ = pinned_ && assignment_2(builder_, level_ + 1) && result_;
    if (result_ || pinned_) {
      marker_.done(ASSIGNMENT);
    }
    else {
      marker_.rollbackTo();
    }
    result_ = exitErrorRecordingSection(builder_, result_, level_, pinned_, _SECTION_GENERAL_, null);
    return result_ || pinned_;
  }

  // ASSIGNMENT_VALUE?
  private static boolean assignment_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "assignment_2")) return false;
    consumeToken(builder_, ASSIGNMENT_VALUE);
    return true;
  }

  /* ********************************************************** */
  // object_path '{' IGNORED_TEXT? expression* '}' IGNORED_TEXT?
  public static boolean code_block(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "code_block")) return false;
    if (!nextTokenIs(builder_, OBJECT_PATH_ENTITY) && !nextTokenIs(builder_, OBJECT_PATH_SEPARATOR)) return false;
    boolean result_ = false;
    boolean pinned_ = false;
    final Marker marker_ = builder_.mark();
    enterErrorRecordingSection(builder_, level_, _SECTION_GENERAL_);
    result_ = object_path(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CODE_BLOCK_OPERATOR_BEGIN);
    pinned_ = result_; // pin = 2
    result_ = result_ && report_error_(builder_, code_block_2(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, code_block_3(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, consumeToken(builder_, CODE_BLOCK_OPERATOR_END)) && result_;
    result_ = pinned_ && code_block_5(builder_, level_ + 1) && result_;
    if (result_ || pinned_) {
      marker_.done(CODE_BLOCK);
    }
    else {
      marker_.rollbackTo();
    }
    result_ = exitErrorRecordingSection(builder_, result_, level_, pinned_, _SECTION_GENERAL_, null);
    return result_ || pinned_;
  }

  // IGNORED_TEXT?
  private static boolean code_block_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "code_block_2")) return false;
    consumeToken(builder_, IGNORED_TEXT);
    return true;
  }

  // expression*
  private static boolean code_block_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "code_block_3")) return false;
    int offset_ = builder_.getCurrentOffset();
    while (true) {
      if (!expression(builder_, level_ + 1)) break;
      int next_offset_ = builder_.getCurrentOffset();
      if (offset_ == next_offset_) {
        empty_element_parsed_guard_(builder_, offset_, "code_block_3");
        break;
      }
      offset_ = next_offset_;
    }
    return true;
  }

  // IGNORED_TEXT?
  private static boolean code_block_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "code_block_5")) return false;
    consumeToken(builder_, IGNORED_TEXT);
    return true;
  }

  /* ********************************************************** */
  // CONDITION
  public static boolean condition_element(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "condition_element")) return false;
    if (!nextTokenIs(builder_, CONDITION)) return false;
    boolean result_ = false;
    final Marker marker_ = builder_.mark();
    result_ = consumeToken(builder_, CONDITION);
    if (result_) {
      marker_.done(CONDITION_ELEMENT);
    }
    else {
      marker_.rollbackTo();
    }
    return result_;
  }

  /* ********************************************************** */
  // object_path '<' object_path_on_same_line
  public static boolean copying(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "copying")) return false;
    if (!nextTokenIs(builder_, OBJECT_PATH_ENTITY) && !nextTokenIs(builder_, OBJECT_PATH_SEPARATOR)) return false;
    boolean result_ = false;
    boolean pinned_ = false;
    final Marker marker_ = builder_.mark();
    enterErrorRecordingSection(builder_, level_, _SECTION_GENERAL_);
    result_ = object_path(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COPYING_OPERATOR);
    pinned_ = result_; // pin = 2
    result_ = result_ && isObjectPathOnSameLine(builder_, level_ + 1);
    if (result_ || pinned_) {
      marker_.done(COPYING);
    }
    else {
      marker_.rollbackTo();
    }
    result_ = exitErrorRecordingSection(builder_, result_, level_, pinned_, _SECTION_GENERAL_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // value_modification | multiline_value_assignment | copying | unsetting | code_block | assignment
  // | condition_element | include_statement_element
  static boolean expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expression")) return false;
    boolean result_ = false;
    final Marker marker_ = builder_.mark();
    enterErrorRecordingSection(builder_, level_, _SECTION_RECOVER_);
    result_ = value_modification(builder_, level_ + 1);
    if (!result_) result_ = multiline_value_assignment(builder_, level_ + 1);
    if (!result_) result_ = copying(builder_, level_ + 1);
    if (!result_) result_ = unsetting(builder_, level_ + 1);
    if (!result_) result_ = code_block(builder_, level_ + 1);
    if (!result_) result_ = assignment(builder_, level_ + 1);
    if (!result_) result_ = condition_element(builder_, level_ + 1);
    if (!result_) result_ = include_statement_element(builder_, level_ + 1);
    if (!result_) {
      marker_.rollbackTo();
    }
    else {
      marker_.drop();
    }
    result_ = exitErrorRecordingSection(builder_, result_, level_, false, _SECTION_RECOVER_, top_expression_recover_parser_);
    return result_;
  }

  /* ********************************************************** */
  // (expression)*
  static boolean file(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "file")) return false;
    int offset_ = builder_.getCurrentOffset();
    while (true) {
      if (!file_0(builder_, level_ + 1)) break;
      int next_offset_ = builder_.getCurrentOffset();
      if (offset_ == next_offset_) {
        empty_element_parsed_guard_(builder_, offset_, "file");
        break;
      }
      offset_ = next_offset_;
    }
    return true;
  }

  // (expression)
  private static boolean file_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "file_0")) return false;
    boolean result_ = false;
    final Marker marker_ = builder_.mark();
    result_ = expression(builder_, level_ + 1);
    if (!result_) {
      marker_.rollbackTo();
    }
    else {
      marker_.drop();
    }
    return result_;
  }

  /* ********************************************************** */
  // INCLUDE_STATEMENT
  public static boolean include_statement_element(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "include_statement_element")) return false;
    if (!nextTokenIs(builder_, INCLUDE_STATEMENT)) return false;
    boolean result_ = false;
    final Marker marker_ = builder_.mark();
    result_ = consumeToken(builder_, INCLUDE_STATEMENT);
    if (result_) {
      marker_.done(INCLUDE_STATEMENT_ELEMENT);
    }
    else {
      marker_.rollbackTo();
    }
    return result_;
  }

  /* ********************************************************** */
  // object_path MULTILINE_VALUE_OPERATOR_BEGIN IGNORED_TEXT? (MULTILINE_VALUE)* MULTILINE_VALUE_OPERATOR_END IGNORED_TEXT?
  public static boolean multiline_value_assignment(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multiline_value_assignment")) return false;
    if (!nextTokenIs(builder_, OBJECT_PATH_ENTITY) && !nextTokenIs(builder_, OBJECT_PATH_SEPARATOR)) return false;
    boolean result_ = false;
    boolean pinned_ = false;
    final Marker marker_ = builder_.mark();
    enterErrorRecordingSection(builder_, level_, _SECTION_GENERAL_);
    result_ = object_path(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, MULTILINE_VALUE_OPERATOR_BEGIN);
    pinned_ = result_; // pin = 2
    result_ = result_ && report_error_(builder_, multiline_value_assignment_2(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, multiline_value_assignment_3(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, consumeToken(builder_, MULTILINE_VALUE_OPERATOR_END)) && result_;
    result_ = pinned_ && multiline_value_assignment_5(builder_, level_ + 1) && result_;
    if (result_ || pinned_) {
      marker_.done(MULTILINE_VALUE_ASSIGNMENT);
    }
    else {
      marker_.rollbackTo();
    }
    result_ = exitErrorRecordingSection(builder_, result_, level_, pinned_, _SECTION_GENERAL_, null);
    return result_ || pinned_;
  }

  // IGNORED_TEXT?
  private static boolean multiline_value_assignment_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multiline_value_assignment_2")) return false;
    consumeToken(builder_, IGNORED_TEXT);
    return true;
  }

  // (MULTILINE_VALUE)*
  private static boolean multiline_value_assignment_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multiline_value_assignment_3")) return false;
    int offset_ = builder_.getCurrentOffset();
    while (true) {
      if (!multiline_value_assignment_3_0(builder_, level_ + 1)) break;
      int next_offset_ = builder_.getCurrentOffset();
      if (offset_ == next_offset_) {
        empty_element_parsed_guard_(builder_, offset_, "multiline_value_assignment_3");
        break;
      }
      offset_ = next_offset_;
    }
    return true;
  }

  // (MULTILINE_VALUE)
  private static boolean multiline_value_assignment_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multiline_value_assignment_3_0")) return false;
    boolean result_ = false;
    final Marker marker_ = builder_.mark();
    result_ = consumeToken(builder_, MULTILINE_VALUE);
    if (!result_) {
      marker_.rollbackTo();
    }
    else {
      marker_.drop();
    }
    return result_;
  }

  // IGNORED_TEXT?
  private static boolean multiline_value_assignment_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multiline_value_assignment_5")) return false;
    consumeToken(builder_, IGNORED_TEXT);
    return true;
  }

  /* ********************************************************** */
  // '.'? (OBJECT_PATH_ENTITY '.')* OBJECT_PATH_ENTITY
  public static boolean object_path(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "object_path")) return false;
    if (!nextTokenIs(builder_, OBJECT_PATH_ENTITY) && !nextTokenIs(builder_, OBJECT_PATH_SEPARATOR)) return false;
    boolean result_ = false;
    boolean pinned_ = false;
    final Marker marker_ = builder_.mark();
    enterErrorRecordingSection(builder_, level_, _SECTION_GENERAL_);
    result_ = object_path_0(builder_, level_ + 1);
    result_ = result_ && object_path_1(builder_, level_ + 1);
    pinned_ = result_; // pin = 2
    result_ = result_ && consumeToken(builder_, OBJECT_PATH_ENTITY);
    if (result_ || pinned_) {
      marker_.done(OBJECT_PATH);
    }
    else {
      marker_.rollbackTo();
    }
    result_ = exitErrorRecordingSection(builder_, result_, level_, pinned_, _SECTION_GENERAL_, null);
    return result_ || pinned_;
  }

  // '.'?
  private static boolean object_path_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "object_path_0")) return false;
    consumeToken(builder_, OBJECT_PATH_SEPARATOR);
    return true;
  }

  // (OBJECT_PATH_ENTITY '.')*
  private static boolean object_path_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "object_path_1")) return false;
    int offset_ = builder_.getCurrentOffset();
    while (true) {
      if (!object_path_1_0(builder_, level_ + 1)) break;
      int next_offset_ = builder_.getCurrentOffset();
      if (offset_ == next_offset_) {
        empty_element_parsed_guard_(builder_, offset_, "object_path_1");
        break;
      }
      offset_ = next_offset_;
    }
    return true;
  }

  // (OBJECT_PATH_ENTITY '.')
  private static boolean object_path_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "object_path_1_0")) return false;
    return object_path_1_0_0(builder_, level_ + 1);
  }

  // OBJECT_PATH_ENTITY '.'
  private static boolean object_path_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "object_path_1_0_0")) return false;
    boolean result_ = false;
    boolean pinned_ = false;
    final Marker marker_ = builder_.mark();
    enterErrorRecordingSection(builder_, level_, _SECTION_GENERAL_);
    result_ = consumeToken(builder_, OBJECT_PATH_ENTITY);
    result_ = result_ && consumeToken(builder_, OBJECT_PATH_SEPARATOR);
    pinned_ = result_; // pin = 2
    if (!result_ && !pinned_) {
      marker_.rollbackTo();
    }
    else {
      marker_.drop();
    }
    result_ = exitErrorRecordingSection(builder_, result_, level_, pinned_, _SECTION_GENERAL_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // ! new_line_white_space
  static boolean top_expression_recover(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "top_expression_recover")) return false;
    boolean result_ = false;
    final Marker marker_ = builder_.mark();
    enterErrorRecordingSection(builder_, level_, _SECTION_NOT_);
    result_ = !isAfterNewLine(builder_, level_ + 1);
    marker_.rollbackTo();
    result_ = exitErrorRecordingSection(builder_, result_, level_, false, _SECTION_NOT_, null);
    return result_;
  }

  /* ********************************************************** */
  // object_path '>' IGNORED_TEXT?
  public static boolean unsetting(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "unsetting")) return false;
    if (!nextTokenIs(builder_, OBJECT_PATH_ENTITY) && !nextTokenIs(builder_, OBJECT_PATH_SEPARATOR)) return false;
    boolean result_ = false;
    boolean pinned_ = false;
    final Marker marker_ = builder_.mark();
    enterErrorRecordingSection(builder_, level_, _SECTION_GENERAL_);
    result_ = object_path(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, UNSETTING_OPERATOR);
    pinned_ = result_; // pin = 2
    result_ = result_ && unsetting_2(builder_, level_ + 1);
    if (result_ || pinned_) {
      marker_.done(UNSETTING);
    }
    else {
      marker_.rollbackTo();
    }
    result_ = exitErrorRecordingSection(builder_, result_, level_, pinned_, _SECTION_GENERAL_, null);
    return result_ || pinned_;
  }

  // IGNORED_TEXT?
  private static boolean unsetting_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "unsetting_2")) return false;
    consumeToken(builder_, IGNORED_TEXT);
    return true;
  }

  /* ********************************************************** */
  // object_path ':=' MODIFICATION_OPERATOR_FUNCTION MODIFICATION_OPERATOR_FUNCTION_PARAM_BEGIN
  //   MODIFICATION_OPERATOR_FUNCTION_ARGUMENT MODIFICATION_OPERATOR_FUNCTION_PARAM_END
  public static boolean value_modification(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "value_modification")) return false;
    if (!nextTokenIs(builder_, OBJECT_PATH_ENTITY) && !nextTokenIs(builder_, OBJECT_PATH_SEPARATOR)) return false;
    boolean result_ = false;
    boolean pinned_ = false;
    final Marker marker_ = builder_.mark();
    enterErrorRecordingSection(builder_, level_, _SECTION_GENERAL_);
    result_ = object_path(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, MODIFICATION_OPERATOR);
    pinned_ = result_; // pin = 2
    result_ = result_ && report_error_(builder_, consumeToken(builder_, MODIFICATION_OPERATOR_FUNCTION));
    result_ = pinned_ && report_error_(builder_, consumeToken(builder_, MODIFICATION_OPERATOR_FUNCTION_PARAM_BEGIN)) && result_;
    result_ = pinned_ && report_error_(builder_, consumeToken(builder_, MODIFICATION_OPERATOR_FUNCTION_ARGUMENT)) && result_;
    result_ = pinned_ && consumeToken(builder_, MODIFICATION_OPERATOR_FUNCTION_PARAM_END) && result_;
    if (result_ || pinned_) {
      marker_.done(VALUE_MODIFICATION);
    }
    else {
      marker_.rollbackTo();
    }
    result_ = exitErrorRecordingSection(builder_, result_, level_, pinned_, _SECTION_GENERAL_, null);
    return result_ || pinned_;
  }

  final static Parser top_expression_recover_parser_ = new Parser() {
      public boolean parse(PsiBuilder builder_, int level_) {
        return top_expression_recover(builder_, level_ + 1);
      }
    };
}
