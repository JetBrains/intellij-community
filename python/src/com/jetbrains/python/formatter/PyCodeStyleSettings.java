/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.formatter;

import com.intellij.formatting.WrapType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.jetbrains.python.PyBundle;
import org.intellij.lang.annotations.MagicConstant;

/**
 * @author yole
 */
public class PyCodeStyleSettings extends CustomCodeStyleSettings {

  public enum DictAlignment {
    NONE(PyBundle.message("formatter.panel.dict.alignment.do.not.align")),
    ON_VALUE(PyBundle.message("formatter.panel.dict.alignment.align.on.value")),
    ON_COLON(PyBundle.message("formatter.panel.dict.alignment.align.on.colon"));

    String description;

    DictAlignment(String description) {
      this.description = description;
    }

    public int asInt() {
      return ordinal();
    }

    @Override
    public String toString() {
      return description;
    }
  }

  // Unfortunately, the old serializer for code style settings can't handle enums
  public static final int DICT_ALIGNMENT_NONE = DictAlignment.NONE.asInt();
  public static final int DICT_ALIGNMENT_ON_VALUE = DictAlignment.ON_VALUE.asInt();
  public static final int DICT_ALIGNMENT_ON_COLON = DictAlignment.ON_COLON.asInt();

  public boolean SPACE_WITHIN_BRACES = false;
  public boolean SPACE_BEFORE_PY_COLON = false;
  public boolean SPACE_AFTER_PY_COLON = true;
  public boolean SPACE_BEFORE_LBRACKET = false;
  public boolean SPACE_AROUND_EQ_IN_NAMED_PARAMETER = false;
  public boolean SPACE_AROUND_EQ_IN_KEYWORD_ARGUMENT = false;
  public boolean SPACE_AROUND_POWER_OPERATOR = true;
  public boolean SPACE_BEFORE_BACKSLASH = true;

  public int BLANK_LINES_AROUND_TOP_LEVEL_CLASSES_FUNCTIONS = 2;
  public int BLANK_LINES_BEFORE_FIRST_METHOD = 0;
  // TODO make boolean (it needs special editor in BlankLinesPanel)
  public boolean BLANK_LINE_AT_FILE_END = true;

  public boolean ALIGN_COLLECTIONS_AND_COMPREHENSIONS = true;
  public boolean ALIGN_MULTILINE_IMPORTS = true;

  public boolean NEW_LINE_AFTER_COLON = false;
  public boolean NEW_LINE_AFTER_COLON_MULTI_CLAUSE = true;

  public boolean SPACE_AFTER_NUMBER_SIGN = true;
  public boolean SPACE_BEFORE_NUMBER_SIGN = true;

  public int DICT_ALIGNMENT = DICT_ALIGNMENT_NONE;
  @MagicConstant(intValues = {
    CommonCodeStyleSettings.DO_NOT_WRAP,
    CommonCodeStyleSettings.WRAP_AS_NEEDED,
    CommonCodeStyleSettings.WRAP_ALWAYS,
    CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
  })
  public int DICT_WRAPPING = WrapType.NORMAL.getLegacyRepresentation();
  public boolean DICT_NEW_LINE_AFTER_LEFT_BRACE = false;
  public boolean DICT_NEW_LINE_BEFORE_RIGHT_BRACE = false;

  public int BLANK_LINES_AFTER_LOCAL_IMPORTS = 0;
  /**
   * Code style for most languages use continuation indent both for parameters in function definition and for arguments in function calls.
   * In Python continuation indent (assuming it's 8 spaces) for parameters is required by PEP 8, because otherwise they won't be visually 
   * distinctive from function body. However for arguments (except several special cases) both normal and continuation indents are acceptable 
   * (as long as they're multiple of 4), though examples in PEP 8 itself use mostly normal indent. Nonetheless, some users prefer to have 
   * the same indentation level for arguments as for parameters.
   */
  public boolean USE_CONTINUATION_INDENT_FOR_ARGUMENTS = false;

  public boolean OPTIMIZE_IMPORTS_SORT_IMPORTS = true;
  public boolean OPTIMIZE_IMPORTS_SORT_NAMES_IN_FROM_IMPORTS = false;
  public boolean OPTIMIZE_IMPORTS_SORT_BY_TYPE_FIRST = true;
  public boolean OPTIMIZE_IMPORTS_JOIN_FROM_IMPORTS_WITH_SAME_SOURCE = false;

  /**
   * Affects wrapping of multiple imported names in a single "from" import.
   */
  @MagicConstant(intValues = {
    CommonCodeStyleSettings.DO_NOT_WRAP,
    CommonCodeStyleSettings.WRAP_AS_NEEDED,
    CommonCodeStyleSettings.WRAP_ALWAYS,
    CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
  })
  public int FROM_IMPORT_WRAPPING = WrapType.NORMAL.getLegacyRepresentation();
  public boolean FROM_IMPORT_NEW_LINE_AFTER_LEFT_PARENTHESIS = false;
  public boolean FROM_IMPORT_NEW_LINE_BEFORE_RIGHT_PARENTHESIS = false;
  
  @MagicConstant(intValues = {
    CommonCodeStyleSettings.DO_NOT_FORCE,
    CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE,
    CommonCodeStyleSettings.FORCE_BRACES_ALWAYS}
  )
  public boolean FROM_IMPORT_PARENTHESES_FORCE_IF_MULTILINE = false;
  public boolean FROM_IMPORT_TRAILING_COMMA_IF_MULTILINE = false;

  /**
   * Corresponds to the option of pycodestyle.py "--hang-closing". Basically, it means that the closing brace of a collection literal, 
   * a comprehension, an argument list, a parameter list or parentheses in "from" import statement should have the same indent as the items 
   * inside even if there is so called hanging indent (nothing follows the opening bracket on its line).
   */
  public boolean HANG_CLOSING_BRACKETS = false;

  public PyCodeStyleSettings(CodeStyleSettings container) {
    super("Python", container);
  }
}
