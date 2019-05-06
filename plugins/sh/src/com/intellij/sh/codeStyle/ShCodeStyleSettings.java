package com.intellij.sh.codeStyle;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;

public class ShCodeStyleSettings extends CustomCodeStyleSettings {
  public boolean BINARY_OPS_START_LINE = false;
  public boolean SWITCH_CASES_INDENTED = false;
  public boolean REDIRECT_FOLLOWED_BY_SPACE = false;
  public boolean KEEP_COLUMN_ALIGNMENT_PADDING = false;
  public boolean MINIFY_PROGRAM = false;
  public String SHFMT_PATH = "";

  public ShCodeStyleSettings(CodeStyleSettings container) {
    super("Shell_Script", container);
  }
}
