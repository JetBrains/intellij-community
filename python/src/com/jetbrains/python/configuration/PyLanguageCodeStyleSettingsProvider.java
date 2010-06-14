package com.jetbrains.python.configuration;

import com.intellij.application.options.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.CodeStyleCustomizationsConsumer;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.formatter.PyCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.codeStyle.CodeStyleCustomizationsConsumer.*;

/**
 * @author yole
 */
public class PyLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
  @Override
  public Language getLanguage() {
    return PythonLanguage.getInstance();
  }

  @Override
  public String getCodeSample(@NotNull SettingsType settingsType) {
    //noinspection EnumSwitchStatementWhichMissesCases
    switch(settingsType) {
      case SPACING_SETTINGS:
        return SPACING_SETTINGS_PREVIEW;

      case BLANK_LINE_SETTINGS:
        return BLANK_LINES_SETTINGS_PREVIEW;

      default:
        return "";
    }
  }

  public void customizeSpacingOptions(CodeStyleCustomizationsConsumer consumer) {
    consumer.showStandardOptions("SPACE_BEFORE_METHOD_CALL_PARENTHESES",
                                 "SPACE_AROUND_ASSIGNMENT_OPERATORS",
                                 "SPACE_AROUND_LOGICAL_OPERATORS",
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
    consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_BEFORE_LBRACKET", "Left bracket", BEFORE_PARENTHESES);    
    consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_AROUND_EQ_IN_NAMED_PARAMETER", "Around = in named parameter", AROUND_OPERATORS);
    consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_AROUND_EQ_IN_KEYWORD_ARGUMENT", "Around = in keyword argument", AROUND_OPERATORS);
    consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_WITHIN_BRACES", "Within braces", OTHER);
    consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_BEFORE_COLON", ApplicationBundle.message("checkbox.spaces.before.colon"), OTHER);
  }

  @Override
  public void customizeBlankLinesOptions(CodeStyleCustomizationsConsumer consumer) {
    consumer.showStandardOptions("BLANK_LINES_AROUND_CLASS",
                                 "BLANK_LINES_AROUND_METHOD",
                                 "BLANK_LINES_AFTER_IMPORTS",
                                 "KEEP_BLANK_LINES_IN_DECLARATIONS",
                                 "KEEP_BLANK_LINES_IN_CODE");
  }

  private static String SPACING_SETTINGS_PREVIEW = "def settings_preview(argument, key=value):\n" +
                                                   "    dict = {1:'a', 2:'b', 3:'c'}\n" +
                                                   "    x = dict[1]\n" +
                                                   "    expr = (1+2)*3 << 4 & 16\n" +
                                                   "    if expr == 0 or abs(expr) < 0: return;\n" +
                                                   "    settings_preview(key=1)";

  private static String BLANK_LINES_SETTINGS_PREVIEW = "import os\n" +
                                                       "class C(object):\n" +
                                                       "    x = 1\n" +
                                                       "    def foo(self):\n" +
                                                       "        pass";
}
