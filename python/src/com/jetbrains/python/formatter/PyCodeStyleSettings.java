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
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.jetbrains.python.PyBundle;

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
  // TODO make boolean (it needs special editor in BlankLinesPanel)
  public boolean BLANK_LINE_AT_FILE_END = true;

  public boolean ALIGN_COLLECTIONS_AND_COMPREHENSIONS = true;
  public boolean ALIGN_MULTILINE_IMPORTS = true;

  public boolean NEW_LINE_AFTER_COLON = false;
  public boolean NEW_LINE_AFTER_COLON_MULTI_CLAUSE = true;

  public boolean SPACE_AFTER_NUMBER_SIGN = true;
  public boolean SPACE_BEFORE_NUMBER_SIGN = true;

  public int DICT_ALIGNMENT = DICT_ALIGNMENT_NONE;
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

  public PyCodeStyleSettings(CodeStyleSettings container) {
    super("Python", container);
  }
}
