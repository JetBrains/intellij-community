// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.formatter;

import com.intellij.application.options.CodeStyleAbstractConfigurable;
import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.application.options.IndentOptionsEditor;
import com.intellij.application.options.SmartIndentOptionsEditor;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.*;
import com.jetbrains.python.PySyntaxBundle;
import com.jetbrains.python.PythonLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable.WRAP_VALUES;
import static com.intellij.psi.codeStyle.CodeStyleSettingsCustomizableOptions.getInstance;


public final class PyLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
  @Override
  public @NotNull Language getLanguage() {
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
                                PySyntaxBundle.message("formatter.left.bracket"), getInstance().SPACES_BEFORE_PARENTHESES);
      consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_AROUND_POWER_OPERATOR",
                                PySyntaxBundle.message("formatter.around.power.operator"), getInstance().SPACES_AROUND_OPERATORS);
      consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_AROUND_EQ_IN_NAMED_PARAMETER",
                                PySyntaxBundle.message("formatter.around.eq.in.named.parameter"), getInstance().SPACES_AROUND_OPERATORS);
      consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_AROUND_EQ_IN_KEYWORD_ARGUMENT",
                                PySyntaxBundle.message("formatter.around.eq.in.keyword.argument"), getInstance().SPACES_AROUND_OPERATORS);
      consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_WITHIN_BRACES", PySyntaxBundle.message("formatter.braces"),
                                getInstance().SPACES_WITHIN);
      consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_BEFORE_PY_COLON",
                                ApplicationBundle.message("checkbox.spaces.before.colon"), getInstance().SPACES_OTHER);
      consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_AFTER_PY_COLON",
                                ApplicationBundle.message("checkbox.spaces.after.colon"), getInstance().SPACES_OTHER);
      consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_BEFORE_BACKSLASH",
                                PySyntaxBundle.message("formatter.before.backslash"), getInstance().SPACES_OTHER);
      consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_BEFORE_NUMBER_SIGN",
                                PySyntaxBundle.message("formatter.before.hash"), getInstance().SPACES_OTHER);
      consumer.showCustomOption(PyCodeStyleSettings.class, "SPACE_AFTER_NUMBER_SIGN",
                                PySyntaxBundle.message("formatter.after.hash"), getInstance().SPACES_OTHER);
      consumer.renameStandardOption("SPACE_AROUND_MULTIPLICATIVE_OPERATORS", PySyntaxBundle.message("formatter.around.multiplicative.operators"));
    }
    else if (settingsType == SettingsType.BLANK_LINES_SETTINGS) {
      consumer.showStandardOptions("BLANK_LINES_AROUND_CLASS",
                                   "BLANK_LINES_AROUND_METHOD",
                                   "BLANK_LINES_AFTER_IMPORTS",
                                   "KEEP_BLANK_LINES_IN_DECLARATIONS",
                                   "KEEP_BLANK_LINES_IN_CODE");
      consumer.renameStandardOption("BLANK_LINES_AFTER_IMPORTS", PySyntaxBundle.message("formatter.around.top.level.imports"));

      consumer.showCustomOption(PyCodeStyleSettings.class, "BLANK_LINES_AROUND_TOP_LEVEL_CLASSES_FUNCTIONS",
                                PySyntaxBundle.message("formatter.around.top.level.classes.and.function"), getInstance().BLANK_LINES);
      consumer.showCustomOption(PyCodeStyleSettings.class, "BLANK_LINES_AFTER_LOCAL_IMPORTS",
                                PySyntaxBundle.message("formatter.after.local.imports"), getInstance().BLANK_LINES);
      consumer.showCustomOption(PyCodeStyleSettings.class, "BLANK_LINES_BEFORE_FIRST_METHOD",
                                PySyntaxBundle.message("formatter.before.first.method"), getInstance().BLANK_LINES);
    }
    else if (settingsType == SettingsType.WRAPPING_AND_BRACES_SETTINGS) {
      consumer.showStandardOptions("RIGHT_MARGIN",
                                   "WRAP_ON_TYPING",
                                   "KEEP_LINE_BREAKS",
                                   "WRAP_LONG_LINES",
                                   "CALL_PARAMETERS_WRAP",
                                   "CALL_PARAMETERS_LPAREN_ON_NEXT_LINE",
                                   "CALL_PARAMETERS_RPAREN_ON_NEXT_LINE",
                                   "ALIGN_MULTILINE_PARAMETERS",
                                   "METHOD_PARAMETERS_WRAP",
                                   "METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE",
                                   "METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE",
                                   "ALIGN_MULTILINE_PARAMETERS_IN_CALLS");

      consumer.showCustomOption(PyCodeStyleSettings.class,
                                "USE_TRAILING_COMMA_IN_ARGUMENTS_LIST",
                                PySyntaxBundle.message("formatter.force.trailing.comma.if.multiline"),
                                ApplicationBundle.message("wrapping.method.arguments"));

      consumer.showCustomOption(PyCodeStyleSettings.class,
                                "USE_TRAILING_COMMA_IN_PARAMETER_LIST",
                                PySyntaxBundle.message("formatter.force.trailing.comma.if.multiline"),
                                ApplicationBundle.message("wrapping.method.parameters"));

      consumer.showCustomOption(PyCodeStyleSettings.class, "NEW_LINE_AFTER_COLON",
                                PySyntaxBundle.message("formatter.single.clause.statements"),
                                PySyntaxBundle.message("formatter.force.new.line.after.colon"));
      consumer.showCustomOption(PyCodeStyleSettings.class, "NEW_LINE_AFTER_COLON_MULTI_CLAUSE",
                                PySyntaxBundle.message("formatter.multi.clause.statements"),
                                PySyntaxBundle.message("formatter.force.new.line.after.colon"));
      consumer.showCustomOption(PyCodeStyleSettings.class, "ALIGN_COLLECTIONS_AND_COMPREHENSIONS",
                                PySyntaxBundle.message("formatter.align.when.multiline"),
                                PySyntaxBundle.message("formatter.collections.and.comprehensions"));
      consumer.showCustomOption(PyCodeStyleSettings.class,
                                "USE_TRAILING_COMMA_IN_COLLECTIONS",
                                PySyntaxBundle.message("formatter.force.trailing.comma.if.multiline"),
                                PySyntaxBundle.message("formatter.collections.and.comprehensions"));

      consumer.showCustomOption(PyCodeStyleSettings.class, "FROM_IMPORT_WRAPPING",
                                PySyntaxBundle.message("formatter.from.import.statements"), null, getInstance().WRAP_OPTIONS, WRAP_VALUES);
      consumer.showCustomOption(PyCodeStyleSettings.class, "ALIGN_MULTILINE_IMPORTS",
                                PySyntaxBundle.message("formatter.align.when.multiline"),
                                PySyntaxBundle.message("formatter.from.import.statements"));
      consumer.showCustomOption(PyCodeStyleSettings.class, "FROM_IMPORT_NEW_LINE_AFTER_LEFT_PARENTHESIS",
                                ApplicationBundle.message("wrapping.new.line.after.lpar"),
                                PySyntaxBundle.message("formatter.from.import.statements"));
      consumer.showCustomOption(PyCodeStyleSettings.class, "FROM_IMPORT_NEW_LINE_BEFORE_RIGHT_PARENTHESIS",
                                ApplicationBundle.message("wrapping.rpar.on.new.line"),
                                PySyntaxBundle.message("formatter.from.import.statements"));
      consumer.showCustomOption(PyCodeStyleSettings.class, "FROM_IMPORT_PARENTHESES_FORCE_IF_MULTILINE",
                                PySyntaxBundle.message("formatter.from.import.statements.force.parentheses.if.multiline"),
                                PySyntaxBundle.message("formatter.from.import.statements"));
      consumer.showCustomOption(PyCodeStyleSettings.class, "FROM_IMPORT_TRAILING_COMMA_IF_MULTILINE",
                                PySyntaxBundle.message("formatter.force.trailing.comma.if.multiline"),
                                PySyntaxBundle.message("formatter.from.import.statements"));

      consumer.showCustomOption(PyCodeStyleSettings.class, "LIST_WRAPPING",
                                PySyntaxBundle.message("formatter.list.literals"), null, getInstance().WRAP_OPTIONS, WRAP_VALUES);
      consumer.showCustomOption(PyCodeStyleSettings.class, "LIST_NEW_LINE_AFTER_LEFT_BRACKET",
                                ApplicationBundle.message("wrapping.new.line.after.lbracket"), PySyntaxBundle.message("formatter.list.literals"));
      consumer.showCustomOption(PyCodeStyleSettings.class, "LIST_NEW_LINE_BEFORE_RIGHT_BRACKET",
                                ApplicationBundle.message("wrapping.rbracket.on.new.line"), PySyntaxBundle.message("formatter.list.literals"));

      consumer.showCustomOption(PyCodeStyleSettings.class, "SET_WRAPPING",
                                PySyntaxBundle.message("formatter.set.literals"), null, getInstance().WRAP_OPTIONS, WRAP_VALUES);
      consumer.showCustomOption(PyCodeStyleSettings.class, "SET_NEW_LINE_AFTER_LEFT_BRACE",
                                ApplicationBundle.message("wrapping.new.line.after.lbrace"), PySyntaxBundle.message("formatter.set.literals"));
      consumer.showCustomOption(PyCodeStyleSettings.class, "SET_NEW_LINE_BEFORE_RIGHT_BRACE",
                                ApplicationBundle.message("wrapping.rbrace.on.new.line"), PySyntaxBundle.message("formatter.set.literals"));

      consumer.showCustomOption(PyCodeStyleSettings.class, "TUPLE_WRAPPING",
                                PySyntaxBundle.message("formatter.parenthesized.tuples"), null, getInstance().WRAP_OPTIONS, WRAP_VALUES);
      consumer.showCustomOption(PyCodeStyleSettings.class, "TUPLE_NEW_LINE_AFTER_LEFT_PARENTHESIS",
                                ApplicationBundle.message("wrapping.new.line.after.lpar"), PySyntaxBundle.message(
          "formatter.parenthesized.tuples"));
      consumer.showCustomOption(PyCodeStyleSettings.class, "TUPLE_NEW_LINE_BEFORE_RIGHT_PARENTHESIS",
                                ApplicationBundle.message("wrapping.rpar.on.new.line"), PySyntaxBundle.message(
          "formatter.parenthesized.tuples"));


      consumer.showCustomOption(PyCodeStyleSettings.class, "DICT_WRAPPING",
                                PySyntaxBundle.message("formatter.dictionary.literals"), null, getInstance().WRAP_OPTIONS, WRAP_VALUES);
      consumer.showCustomOption(PyCodeStyleSettings.class, "DICT_NEW_LINE_AFTER_LEFT_BRACE",
                                ApplicationBundle.message("wrapping.new.line.after.lbrace"),
                                PySyntaxBundle.message("formatter.dictionary.literals"));
      consumer.showCustomOption(PyCodeStyleSettings.class, "DICT_NEW_LINE_BEFORE_RIGHT_BRACE",
                                ApplicationBundle.message("wrapping.rbrace.on.new.line"),
                                PySyntaxBundle.message("formatter.dictionary.literals"));
      consumer
        .showCustomOption(PyCodeStyleSettings.class, "HANG_CLOSING_BRACKETS", PySyntaxBundle.message("formatter.hang.closing.brackets"), null);
    }
  }

  @Override
  public IndentOptionsEditor getIndentOptionsEditor() {
    return new SmartIndentOptionsEditor();
  }

  @Override
  protected void customizeDefaults(@NotNull CommonCodeStyleSettings commonSettings,
                                   @NotNull CommonCodeStyleSettings.IndentOptions indentOptions) {
    indentOptions.INDENT_SIZE = 4;
    commonSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    commonSettings.KEEP_BLANK_LINES_IN_DECLARATIONS = 1;
    // Don't set it to 2 -- this setting is used implicitly in a lot of methods related to spacing,
    // e.g. in SpacingBuilder#blankLines(), and can lead to unexpected side-effects in formatter's
    // behavior
    commonSettings.KEEP_BLANK_LINES_IN_CODE = 1;
    commonSettings.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    commonSettings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
  }

  @Override
  public @Nullable CustomCodeStyleSettings createCustomSettings(@NotNull CodeStyleSettings settings) {
    return new PyCodeStyleSettings(settings);
  }

  @Override
  public @NotNull CodeStyleConfigurable createConfigurable(@NotNull CodeStyleSettings baseSettings, @NotNull CodeStyleSettings modelSettings) {
    return new CodeStyleAbstractConfigurable(baseSettings, modelSettings,
                                             PySyntaxBundle.message("configurable.PyLanguageCodeStyleSettingsProvider.display.name")) {
      @Override
      protected @NotNull CodeStyleAbstractPanel createPanel(final @NotNull CodeStyleSettings settings) {
        return new PyCodeStyleMainPanel(getCurrentSettings(), settings);
      }

      @Override
      public String getHelpTopic() {
        return "reference.settingsdialog.codestyle.python";
      }
    };
  }

  private static final String SPACING_SETTINGS_PREVIEW = """
    def settings_preview(argument, key=value):
        dict = {1:'a', 2:'b', 3:'c'}
        x = dict[1]
        expr = (1+2)*3 << 4**5 & 16
        if expr == 0 or abs(expr) < 0: print('weird'); return
        settings_preview(key=1)

    foo =\\
        bar

    def no_params():
        return globals()""";

  private static final String BLANK_LINES_SETTINGS_PREVIEW = """
    import os
    class C(object):
        import sys
        x = 1
        def foo(self):
            import platform
            print(platform.processor())""";
  private static final String WRAP_SETTINGS_PREVIEW = """
    from module import foo, bar, baz, quux

    long_expression = component_one + component_two + component_three + component_four + component_five + component_six

    def xyzzy(a1, a2, long_parameter_1, a3, a4, long_parameter_2):
        pass

    xyzzy(1, 2, 'long_string_constant1', 3, 4, 'long_string_constant2')

    xyzzy(
        'with',
        'hanging',
          'indent'
    )
    attrs = [e.attr for e in
        items]
    
    num_dict = {"one": 1, "two": 2, "three": 3, "four": 4, "five": 5}
    
    colors = ['red', 'green', 'blue', 'black', 'white', 'gray']
    
    star_names = {"Sirius", "Betelgeuse", "Polaris", "Vega", "Arcturus", "Aldebaran"}
    
    planets = ("Mercury", "Venus", "Earth", "Mars", "Jupiter", "Saturn", "Uranus", "Neptune")

    ingredients = [
        'green',
        'eggs',
    ]

    if True: pass

    try: pass
    finally: pass
    """;
  private static final String INDENT_SETTINGS_PREVIEW = """
    def foo():
        print('bar')

    def long_function_name(
            var_one, var_two, var_three,
            var_four):
        print(var_one)""";
}
