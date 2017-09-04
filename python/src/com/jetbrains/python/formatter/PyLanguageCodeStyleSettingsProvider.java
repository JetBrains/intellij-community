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

import com.intellij.application.options.IndentOptionsEditor;
import com.intellij.application.options.SmartIndentOptionsEditor;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.DisplayPriority;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.util.PlatformUtils;
import com.jetbrains.python.PyBundle;
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
    if (settingsType == SettingsType.INDENT_SETTINGS) return INDENT_SETTINGS_PREVIEW;
    return "";
  }

  @Override
  public void customizeSettings(@NotNull CodeStyleSettingsCustomizable consumer, @NotNull SettingsType settingsType) {
    if (settingsType == SettingsType.SPACING_SETTINGS) {
      consumer.showStandardOptions("SPACE_BEFORE_METHOD_CALL_PARENTHESES",
                                   "SPACE_BEFORE_METHOD_PARENTHESES",
                                   "SPACE_AROUND_ASSIGNMENT_OPERATORS",
                                   "SPACE_AROUND_EQUALITY_OPERATORS",
                                   "SPACE_AROUND_RELATIONAL_OPERATORS",
                                   "SPACE_AROUND_BITWISE_OPERATORS",
                                   "SPACE_AROUND_ADDITIVE_OPERATORS",
                                   "SPACE_AROUND_MULTIPLICATIVE_OPERATORS",
                                   "SPACE_AROUND_SHIFT_OPERATORS",
                                   "SPACE_WITHIN_METHOD_CALL_PARENTHESES",
                                   "SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES",
                                   "SPACE_WITHIN_METHOD_PARENTHESES",
                                   "SPACE_WITHIN_EMPTY_METHOD_PARENTHESES",
                                   "SPACE_WITHIN_BRACKETS",
                                   "SPACE_AFTER_COMMA",
                                   "SPACE_BEFORE_COMMA",
                                   "SPACE_BEFORE_SEMICOLON");
      consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_BEFORE_LBRACKET",
                                PyBundle.message("formatter.left.bracket"), SPACES_BEFORE_PARENTHESES);
      consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_AROUND_POWER_OPERATOR",
                                PyBundle.message("formatter.around.power.operator"), SPACES_AROUND_OPERATORS);
      consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_AROUND_EQ_IN_NAMED_PARAMETER",
                                PyBundle.message("formatter.around.eq.in.named.parameter"), SPACES_AROUND_OPERATORS);
      consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_AROUND_EQ_IN_KEYWORD_ARGUMENT",
                                PyBundle.message("formatter.around.eq.in.keyword.argument"), SPACES_AROUND_OPERATORS);
      consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_WITHIN_BRACES", PyBundle.message("formatter.braces"), SPACES_WITHIN);
      consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_BEFORE_PY_COLON",
                                ApplicationBundle.message("checkbox.spaces.before.colon"), SPACES_OTHER);
      consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_AFTER_PY_COLON",
                                ApplicationBundle.message("checkbox.spaces.after.colon"), SPACES_OTHER);
      consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_BEFORE_BACKSLASH",
                                PyBundle.message("formatter.before.backslash"), SPACES_OTHER);
      consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_BEFORE_NUMBER_SIGN",
                                PyBundle.message("formatter.before.hash"), SPACES_OTHER);
      consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_AFTER_NUMBER_SIGN",
                                PyBundle.message("formatter.after.hash"), SPACES_OTHER);
      consumer.renameStandardOption("SPACE_AROUND_MULTIPLICATIVE_OPERATORS", PyBundle.message("formatter.around.multiplicative.operators"));
    }
    else if (settingsType == SettingsType.BLANK_LINES_SETTINGS) {
      consumer.showStandardOptions("BLANK_LINES_AROUND_CLASS",
                                   "BLANK_LINES_AROUND_METHOD",
                                   "BLANK_LINES_AFTER_IMPORTS",
                                   "KEEP_BLANK_LINES_IN_DECLARATIONS",
                                   "KEEP_BLANK_LINES_IN_CODE");
      consumer.renameStandardOption("BLANK_LINES_AFTER_IMPORTS", PyBundle.message("formatter.around.top.level.imports"));

      consumer.showCustomOption(PyCodeStyleSettings.class, "BLANK_LINES_AROUND_TOP_LEVEL_CLASSES_FUNCTIONS",
                                PyBundle.message("formatter.around.top.level.classes.and.function"), BLANK_LINES);
      consumer.showCustomOption(PyCodeStyleSettings.class, "BLANK_LINES_AFTER_LOCAL_IMPORTS",
                                PyBundle.message("formatter.after.local.imports"), BLANK_LINES);
      consumer.showCustomOption(PyCodeStyleSettings.class, "BLANK_LINES_BEFORE_FIRST_METHOD",
                                PyBundle.message("formatter.before.first.method"), BLANK_LINES);
    }
    else if (settingsType == SettingsType.WRAPPING_AND_BRACES_SETTINGS) {
      consumer.showStandardOptions("RIGHT_MARGIN",
                                   "WRAP_ON_TYPING",
                                   "KEEP_LINE_BREAKS",
                                   "WRAP_LONG_LINES",
                                   "ALIGN_MULTILINE_PARAMETERS",
                                   "ALIGN_MULTILINE_PARAMETERS_IN_CALLS");
      consumer.showCustomOption(PyCodeStyleSettings.class, "NEW_LINE_AFTER_COLON",
                                PyBundle.message("formatter.single.clause.statements"),
                                PyBundle.message("formatter.force.new.line.after.colon"));
      consumer.showCustomOption(PyCodeStyleSettings.class, "NEW_LINE_AFTER_COLON_MULTI_CLAUSE",
                                PyBundle.message("formatter.multi.clause.statements"),
                                PyBundle.message("formatter.force.new.line.after.colon"));
      consumer.showCustomOption(PyCodeStyleSettings.class, "ALIGN_COLLECTIONS_AND_COMPREHENSIONS",
                                PyBundle.message("formatter.align.when.multiline"),
                                PyBundle.message("formatter.collections.and.comprehensions"));
      
      consumer.showCustomOption(PyCodeStyleSettings.class, "FROM_IMPORT_WRAPPING",
                                PyBundle.message("formatter.from.import.statements"), null, WRAP_OPTIONS, WRAP_VALUES);
      consumer.showCustomOption(PyCodeStyleSettings.class, "ALIGN_MULTILINE_IMPORTS",
                                PyBundle.message("formatter.align.when.multiline"),
                                PyBundle.message("formatter.from.import.statements"));
      consumer.showCustomOption(PyCodeStyleSettings.class, "FROM_IMPORT_NEW_LINE_AFTER_LEFT_PARENTHESIS",
                                ApplicationBundle.message("wrapping.new.line.after.lpar"),
                                PyBundle.message("formatter.from.import.statements"));
      consumer.showCustomOption(PyCodeStyleSettings.class, "FROM_IMPORT_NEW_LINE_BEFORE_RIGHT_PARENTHESIS",
                                ApplicationBundle.message("wrapping.rpar.on.new.line"),
                                PyBundle.message("formatter.from.import.statements"));
      consumer.showCustomOption(PyCodeStyleSettings.class, "FROM_IMPORT_PARENTHESES_FORCE_IF_MULTILINE",
                                PyBundle.message("formatter.from.import.statements.force.parentheses.if.multiline"),
                                PyBundle.message("formatter.from.import.statements"));
      consumer.showCustomOption(PyCodeStyleSettings.class, "FROM_IMPORT_TRAILING_COMMA_IF_MULTILINE",
                                PyBundle.message("formatter.from.import.statements.force.comma.if.multline"),
                                PyBundle.message("formatter.from.import.statements"));

      consumer.showCustomOption(PyCodeStyleSettings.class, "DICT_WRAPPING",
                                PyBundle.message("formatter.dictionary.literals"), null, WRAP_OPTIONS, WRAP_VALUES);
      consumer.showCustomOption(PyCodeStyleSettings.class, "DICT_NEW_LINE_AFTER_LEFT_BRACE",
                                ApplicationBundle.message("wrapping.new.line.after.lbrace"),
                                PyBundle.message("formatter.dictionary.literals"));
      consumer.showCustomOption(PyCodeStyleSettings.class, "DICT_NEW_LINE_BEFORE_RIGHT_BRACE",
                                ApplicationBundle.message("wrapping.rbrace.on.new.line"),
                                PyBundle.message("formatter.dictionary.literals"));
      consumer.showCustomOption(PyCodeStyleSettings.class, "HANG_CLOSING_BRACKETS", PyBundle.message("formatter.hang.closing.brackets"), null);
    }
  }

  @Override
  public IndentOptionsEditor getIndentOptionsEditor() {
    return new SmartIndentOptionsEditor();
  }

  @Override
  public CommonCodeStyleSettings getDefaultCommonSettings() {
    CommonCodeStyleSettings defaultSettings = new CommonCodeStyleSettings(PythonLanguage.getInstance());
    CommonCodeStyleSettings.IndentOptions indentOptions = defaultSettings.initIndentOptions();
    indentOptions.INDENT_SIZE = 4;
    defaultSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    defaultSettings.KEEP_BLANK_LINES_IN_DECLARATIONS = 1;
    // Don't set it to 2 -- this setting is used implicitly in a lot of methods related to spacing,
    // e.g. in SpacingBuilder#blankLines(), and can lead to unexpected side-effects in formatter's
    // behavior
    defaultSettings.KEEP_BLANK_LINES_IN_CODE = 1;
    return defaultSettings;
  }

  @Override
  public DisplayPriority getDisplayPriority() {
    return PlatformUtils.isPyCharm() ? DisplayPriority.KEY_LANGUAGE_SETTINGS : DisplayPriority.LANGUAGE_SETTINGS;
  }

  @SuppressWarnings("FieldCanBeLocal")
  private static String SPACING_SETTINGS_PREVIEW = "def settings_preview(argument, key=value):\n" +
                                                   "    dict = {1:'a', 2:'b', 3:'c'}\n" +
                                                   "    x = dict[1]\n" +
                                                   "    expr = (1+2)*3 << 4**5 & 16\n" +
                                                   "    if expr == 0 or abs(expr) < 0: print('weird'); return\n" +
                                                   "    settings_preview(key=1)\n" +
                                                   "\n" +
                                                   "foo =\\\n" +
                                                   "    bar\n" +
                                                   "\n" +
                                                   "def no_params():\n" +
                                                   "    return globals()";

  @SuppressWarnings("FieldCanBeLocal")
  private static String BLANK_LINES_SETTINGS_PREVIEW = "import os\n" +
                                                       "class C(object):\n" +
                                                       "    import sys\n" +
                                                       "    x = 1\n" +
                                                       "    def foo(self):\n" +
                                                       "        import platform\n" +
                                                       "        print(platform.processor())";
  @SuppressWarnings("FieldCanBeLocal")
  private static String WRAP_SETTINGS_PREVIEW = "from module import foo, bar, baz, quux\n" +
                                                "\n" +
                                                "long_expression = component_one + component_two + component_three + component_four + component_five + component_six\n" +
                                                "\n" +
                                                "def xyzzy(long_parameter_1,\n" +
                                                "long_parameter_2):\n" +
                                                "    pass\n" +
                                                "\n" +
                                                "xyzzy('long_string_constant1',\n" +
                                                "    'long_string_constant2')\n" +
                                                "\n" +
                                                "xyzzy(\n" +
                                                "    'with',\n" +
                                                "    'hanging',\n" +
                                                "      'indent'\n" +
                                                ")\n" +
                                                "attrs = [e.attr for e in\n" +
                                                "    items]\n" +
                                                "\n" +
                                                "ingredients = [\n" +
                                                "    'green',\n" +
                                                "    'eggs',\n" +
                                                "]\n" +
                                                "\n" +
                                                "if True: pass\n" +
                                                "\n" +
                                                "try: pass\n" +
                                                "finally: pass\n";
  @SuppressWarnings("FieldCanBeLocal")
  private static String INDENT_SETTINGS_PREVIEW = "def foo():\n" +
                                                  "    print 'bar'\n\n" +
                                                  "def long_function_name(\n" +
                                                  "        var_one, var_two, var_three,\n" +
                                                  "        var_four):\n" +
                                                  "    print(var_one)";
}
