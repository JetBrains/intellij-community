package com.jetbrains.python.formatter;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.jetbrains.python.PythonLanguage;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable.*;

/**
 * @author yole
 */
public class PyLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
  @NotNull
  @Override
  public Language getLanguage() {
    return PythonLanguage.getInstance();
  }

  @Override
  public String getCodeSample(@NotNull SettingsType settingsType) {
    if (settingsType == SettingsType.SPACING_SETTINGS) return SPACING_SETTINGS_PREVIEW;
    if (settingsType == SettingsType.BLANK_LINES_SETTINGS) return BLANK_LINES_SETTINGS_PREVIEW;
    if (settingsType == SettingsType.WRAPPING_AND_BRACES_SETTINGS) return WRAP_SETTINGS_PREVIEW;
    return "";
  }

  @Override
  public void customizeSettings(@NotNull CodeStyleSettingsCustomizable consumer, @NotNull SettingsType settingsType) {
    if (settingsType == SettingsType.SPACING_SETTINGS) {
      consumer.showStandardOptions("SPACE_BEFORE_METHOD_CALL_PARENTHESES",
                                   "SPACE_AROUND_ASSIGNMENT_OPERATORS",
                                   "SPACE_AROUND_EQUALITY_OPERATORS",
                                   "SPACE_AROUND_RELATIONAL_OPERATORS",
                                   "SPACE_AROUND_BITWISE_OPERATORS",
                                   "SPACE_AROUND_ADDITIVE_OPERATORS",
                                   "SPACE_AROUND_MULTIPLICATIVE_OPERATORS",
                                   "SPACE_AROUND_SHIFT_OPERATORS",
                                   "SPACE_WITHIN_METHOD_CALL_PARENTHESES",
                                   "SPACE_WITHIN_BRACKETS",
                                   "SPACE_AFTER_COMMA",
                                   "SPACE_BEFORE_COMMA",
                                   "SPACE_BEFORE_SEMICOLON");
      consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_BEFORE_LBRACKET", "Left bracket", SPACES_BEFORE_PARENTHESES);
      consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_AROUND_EQ_IN_NAMED_PARAMETER", "Around = in named parameter",
                                SPACES_AROUND_OPERATORS);
      consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_AROUND_EQ_IN_KEYWORD_ARGUMENT", "Around = in keyword argument",
                                SPACES_AROUND_OPERATORS);
      consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_WITHIN_BRACES", "Within braces", SPACES_WITHIN);
      consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_BEFORE_PY_COLON", ApplicationBundle.message("checkbox.spaces.before.colon"), SPACES_OTHER);
      consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_AFTER_PY_COLON", ApplicationBundle.message("checkbox.spaces.after.colon"), SPACES_OTHER);
    }
    else if (settingsType == SettingsType.BLANK_LINES_SETTINGS) {
      consumer.showStandardOptions("BLANK_LINES_AROUND_CLASS",
                                   "BLANK_LINES_AROUND_METHOD",
                                   "BLANK_LINES_AFTER_IMPORTS",
                                   "KEEP_BLANK_LINES_IN_DECLARATIONS",
                                   "KEEP_BLANK_LINES_IN_CODE");
      consumer.showCustomOption(PyCodeStyleSettings.class, "BLANK_LINES_BETWEEN_TOP_LEVEL_CLASSES_FUNCTIONS", "Between top-level classes and functions",
                                BLANK_LINES);
    }
    else if (settingsType == SettingsType.WRAPPING_AND_BRACES_SETTINGS) {
      consumer.showStandardOptions("KEEP_LINE_BREAKS",
                                   "WRAP_LONG_LINES");
    }
  }

  private static String SPACING_SETTINGS_PREVIEW = "def settings_preview(argument, key=value):\n" +
                                                   "    dict = {1:'a', 2:'b', 3:'c'}\n" +
                                                   "    x = dict[1]\n" +
                                                   "    expr = (1+2)*3 << 4 & 16\n" +
                                                   "    if expr == 0 or abs(expr) < 0: print('weird'); return\n" +
                                                   "    settings_preview(key=1)";

  private static String BLANK_LINES_SETTINGS_PREVIEW = "import os\n" +
                                                       "class C(object):\n" +
                                                       "    x = 1\n" +
                                                       "    def foo(self):\n" +
                                                       "        pass";

  private static String WRAP_SETTINGS_PREVIEW = "long_expression = component_one + component_two + component_three + component_four + component_five + component_six";
}
