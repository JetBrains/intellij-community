// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.formatter

import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.jetbrains.python.PySyntaxCoreBundle
import com.jetbrains.python.PythonLanguage

/**
 * The legacy ("classic") Python code style defaults — exactly the behavior that shipped before
 * PY-85946. Every field is set to its historical default so that projects pinned to this profile
 * serialize no extra diffs and format identically to older IDE versions.
 *
 * @see PyDefaultStyleGuide for the new industry-standard defaults.
 */
object PyClassicStyleGuide {
  const val CODE_STYLE_ID: String = "PY_CLASSIC_DEFAULTS"

  val CODE_STYLE_TITLE: @NlsContexts.ListItem String
    get() = PySyntaxCoreBundle.message("python.code.style.classic.title")

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

      LIST_WRAPPING = CommonCodeStyleSettings.WRAP_AS_NEEDED
      SET_WRAPPING = CommonCodeStyleSettings.WRAP_AS_NEEDED
      TUPLE_WRAPPING = CommonCodeStyleSettings.WRAP_AS_NEEDED
      DICT_WRAPPING = CommonCodeStyleSettings.WRAP_AS_NEEDED
      FROM_IMPORT_WRAPPING = CommonCodeStyleSettings.WRAP_AS_NEEDED

      LIST_NEW_LINE_AFTER_LEFT_BRACKET = false
      LIST_NEW_LINE_BEFORE_RIGHT_BRACKET = false
      SET_NEW_LINE_AFTER_LEFT_BRACE = false
      SET_NEW_LINE_BEFORE_RIGHT_BRACE = false
      TUPLE_NEW_LINE_AFTER_LEFT_PARENTHESIS = false
      TUPLE_NEW_LINE_BEFORE_RIGHT_PARENTHESIS = false
      DICT_NEW_LINE_AFTER_LEFT_BRACE = false
      DICT_NEW_LINE_BEFORE_RIGHT_BRACE = false
      FROM_IMPORT_NEW_LINE_AFTER_LEFT_PARENTHESIS = false
      FROM_IMPORT_NEW_LINE_BEFORE_RIGHT_PARENTHESIS = false

      USE_TRAILING_COMMA_IN_COLLECTIONS = false
      USE_TRAILING_COMMA_IN_PARAMETER_LIST = false
      USE_TRAILING_COMMA_IN_ARGUMENTS_LIST = false
      FROM_IMPORT_TRAILING_COMMA_IF_MULTILINE = false
      FROM_IMPORT_PARENTHESES_FORCE_IF_MULTILINE = false

      ALIGN_COLLECTIONS_AND_COMPREHENSIONS = true
      ALIGN_MULTILINE_IMPORTS = true

      USE_CONTINUATION_INDENT_FOR_PARAMETERS = true
      USE_CONTINUATION_INDENT_FOR_ARGUMENTS = false
      USE_CONTINUATION_INDENT_FOR_COLLECTION_AND_COMPREHENSIONS = false

      NEW_LINE_AFTER_COLON = false

      HANG_CLOSING_BRACKETS = false
      DICT_ALIGNMENT = PyCodeStyleSettings.DICT_ALIGNMENT_NONE
    }
  }

  @JvmStatic
  @JvmOverloads
  fun applyToCommonSettings(settings: CommonCodeStyleSettings, modifyCodeStyle: Boolean = true) {
    settings.apply {
      RIGHT_MARGIN = -1
      setSoftMargins(emptyList())
      WRAP_LONG_LINES = false

      CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
      CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = false
      CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = false

      METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
      METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = false
      METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = false

      ALIGN_MULTILINE_PARAMETERS = true
      ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
    }

    if (modifyCodeStyle && settings is PyCommonCodeStyleSettings) {
      settings.CODE_STYLE_PROFILE = CODE_STYLE_ID
    }
  }
}
