package com.jetbrains.python.formatter;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;

/**
 * @author yole
 */
public class PyCodeStyleSettings extends CustomCodeStyleSettings {
  public boolean SPACE_WITHIN_BRACES = false;
  public boolean SPACE_BEFORE_PY_COLON = false;
  public boolean SPACE_AFTER_PY_COLON = true;
  public boolean SPACE_BEFORE_LBRACKET = false;
  public boolean SPACE_AROUND_EQ_IN_NAMED_PARAMETER = false;
  public boolean SPACE_AROUND_EQ_IN_KEYWORD_ARGUMENT = false;

  public int BLANK_LINES_BETWEEN_TOP_LEVEL_CLASSES_FUNCTIONS = 2;

  public PyCodeStyleSettings(CodeStyleSettings container) {
    super("Python", container);
  }
}
