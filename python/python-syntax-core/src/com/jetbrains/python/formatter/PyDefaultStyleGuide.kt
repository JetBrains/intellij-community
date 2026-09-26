// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.formatter

import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.jetbrains.python.PySyntaxCoreBundle
import com.jetbrains.python.PythonLanguage

/**
 * Industry-standard (PEP 8 / Black / Ruff aligned) Python code style defaults.
 *
 * This is the "default" profile introduced by PY-85946. Collections, argument and parameter lists
 * are chopped down one item per line when they don't fit, a magic trailing comma is added, the
 * closing bracket goes on its own (dedented) line, items are no longer aligned to the opening
 * bracket, and parameter lists use a normal (4-space) indent relying on the dedented closing
 * bracket for disambiguation (see [PyBlock]).
 *
 * @see PyClassicStyleGuide for the legacy behavior.
 */
object PyDefaultStyleGuide {
  const val CODE_STYLE_ID: String = "PY_DEFAULT"

  val CODE_STYLE_TITLE: @NlsContexts.ListItem String
    get() = PySyntaxCoreBundle.message("python.code.style.default.title")

  @JvmStatic
  fun apply(settings: CodeStyleSettings) {
    applyToPyCustomSettings(settings.getCustomSettings(PyCodeStyleSettings::class.java))
    applyToCommonSettings(settings.getCommonSettings(PythonLanguage.getInstance()))
  }

  @JvmStatic
  @JvmOverloads
  fun applyToPyCustomSettings(settings: PyCodeStyleSettings, modifyCodeStyle: Boolean = true) {
    settings.apply {
      if (modifyCodeStyle) {
        CODE_STYLE_PROFILE = CODE_STYLE_ID
      }

      // Chop down if long.
      LIST_WRAPPING = PyCodeStyleSettings.CHOP_DOWN_IF_LONG
      SET_WRAPPING = PyCodeStyleSettings.CHOP_DOWN_IF_LONG
      TUPLE_WRAPPING = PyCodeStyleSettings.CHOP_DOWN_IF_LONG
      DICT_WRAPPING = PyCodeStyleSettings.CHOP_DOWN_IF_LONG
      FROM_IMPORT_WRAPPING = PyCodeStyleSettings.CHOP_DOWN_IF_LONG

      // New line after the opening parenthesis and a dedented closing bracket when split.
      LIST_NEW_LINE_AFTER_LEFT_BRACKET = true
      LIST_NEW_LINE_BEFORE_RIGHT_BRACKET = true
      SET_NEW_LINE_AFTER_LEFT_BRACE = true
      SET_NEW_LINE_BEFORE_RIGHT_BRACE = true
      TUPLE_NEW_LINE_AFTER_LEFT_PARENTHESIS = true
      TUPLE_NEW_LINE_BEFORE_RIGHT_PARENTHESIS = true
      DICT_NEW_LINE_AFTER_LEFT_BRACE = true
      DICT_NEW_LINE_BEFORE_RIGHT_BRACE = true
      FROM_IMPORT_NEW_LINE_AFTER_LEFT_PARENTHESIS = true
      FROM_IMPORT_NEW_LINE_BEFORE_RIGHT_PARENTHESIS = true

      // Force a new line after the colon of single-clause compound statements.
      NEW_LINE_AFTER_COLON = true

      // Magic trailing comma.
      USE_TRAILING_COMMA_IN_COLLECTIONS = true
      USE_TRAILING_COMMA_IN_PARAMETER_LIST = true
      USE_TRAILING_COMMA_IN_ARGUMENTS_LIST = true
      FROM_IMPORT_TRAILING_COMMA_IF_MULTILINE = true
      FROM_IMPORT_PARENTHESES_FORCE_IF_MULTILINE = true

      // No alignment to the opening bracket.
      ALIGN_COLLECTIONS_AND_COMPREHENSIONS = false
      ALIGN_MULTILINE_IMPORTS = false

      // Normal (4-space) indent for parameters
      USE_CONTINUATION_INDENT_FOR_PARAMETERS = false
      USE_CONTINUATION_INDENT_FOR_ARGUMENTS = false
      USE_CONTINUATION_INDENT_FOR_COLLECTION_AND_COMPREHENSIONS = false

      HANG_CLOSING_BRACKETS = false
      DICT_ALIGNMENT = PyCodeStyleSettings.DICT_ALIGNMENT_NONE
    }
  }

  @JvmStatic
  @JvmOverloads
  fun applyToCommonSettings(settings: CommonCodeStyleSettings, modifyCodeStyle: Boolean = true) {
    settings.apply {
      RIGHT_MARGIN = 88
      // Visual guides (soft margins) at the same column as the hard wrap.
      setSoftMargins(listOf(88))
      // Ensure right margin is not exceeded.
      WRAP_LONG_LINES = true

      CALL_PARAMETERS_WRAP = PyCodeStyleSettings.CHOP_DOWN_IF_LONG
      CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true
      CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true

      METHOD_PARAMETERS_WRAP = PyCodeStyleSettings.CHOP_DOWN_IF_LONG
      METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = true
      METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = true

      ALIGN_MULTILINE_PARAMETERS = false
      ALIGN_MULTILINE_PARAMETERS_IN_CALLS = false
    }

    if (modifyCodeStyle && settings is PyCommonCodeStyleSettings) {
      settings.CODE_STYLE_PROFILE = CODE_STYLE_ID
    }
  }
}
