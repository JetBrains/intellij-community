package org.jetbrains.plugins.textmate

import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.textmate.language.preferences.TextMateAutoClosingPair
import org.jetbrains.plugins.textmate.language.preferences.TextMateBracePair

interface Constants {
  enum class StringKey(val value: String) {
    CONTENT("content"),
    NAME("name"),
    CONTENT_NAME("contentName"),
    WHILE("while"),
    END("end"),
    SCOPE_NAME("scopeName"),
    MATCH("match"),
    BEGIN("begin");

    companion object {
      fun fromName(name: String): StringKey? {
        for (v in StringKey.entries) {
          if (v.value == name) {
            return v
          }
        }
        return null
      }
    }
  }

  enum class CaptureKey(val value: String) {
    CAPTURES("captures"),
    BEGIN_CAPTURES("beginCaptures"),
    END_CAPTURES("endCaptures");

    companion object {
      fun fromName(name: String): CaptureKey? {
        for (v in CaptureKey.entries) {
          if (v.value == name) {
            return v
          }
        }
        return null
      }
    }
  }

  companion object {
    // KEYS
    const val NAME_KEY: @NonNls String = "name"
    const val VALUE_KEY: @NonNls String = "value"
    const val TAB_TRIGGER_KEY: @NonNls String = "tabTrigger"
    const val FILE_TYPES_KEY: @NonNls String = "fileTypes"

    const val FIRST_LINE_MATCH: @NonNls String = "firstLineMatch"
    const val INCLUDE_KEY: @NonNls String = "include"
    const val REPOSITORY_KEY: @NonNls String = "repository"
    const val PATTERNS_KEY: @NonNls String = "patterns"
    const val INJECTIONS_KEY: @NonNls String = "injections"
    const val SCOPE_KEY: @NonNls String = "scope"
    const val UUID_KEY: @NonNls String = "uuid"
    const val FOREGROUND_KEY: @NonNls String = "foreground"
    const val FONT_STYLE_KEY: @NonNls String = "fontStyle"
    const val BACKGROUND_KEY: @NonNls String = "background"
    const val SHELL_VARIABLES_KEY: @NonNls String = "shellVariables"
    const val DESCRIPTION_KEY: @NonNls String = "description"

    // VALUES
    const val INCLUDE_SELF_VALUE: @NonNls String = "\$self"
    const val INCLUDE_BASE_VALUE: @NonNls String = "\$base"
    const val BOLD_FONT_STYLE: @NonNls String = "bold"
    const val ITALIC_FONT_STYLE: @NonNls String = "italic"
    const val UNDERLINE_FONT_STYLE: @NonNls String = "underline"
    const val SETTINGS_KEY: @NonNls String = "settings"

    // OTHER
    const val BUNDLE_INFO_PLIST_NAME: @NonNls String = "info.plist"
    const val PACKAGE_JSON_NAME: @NonNls String = "package.json"

    // SHELL VARIABLES
    const val COMMENT_START_VARIABLE: @NonNls String = "TM_COMMENT_START"
    const val COMMENT_END_VARIABLE: @NonNls String = "TM_COMMENT_END"


    // PREFERENCES
    const val HIGHLIGHTING_PAIRS_KEY: @NonNls String = "highlightPairs"
    const val SMART_TYPING_PAIRS_KEY: @NonNls String = "smartTypingPairs"
    const val INDENTATION_RULES: @NonNls String = "indentationRules"

    // INDENTATION PATTERNS
    const val INCREASE_INDENT_PATTERN: @NonNls String = "increaseIndentPattern"
    const val DECREASE_INDENT_PATTERN: @NonNls String = "decreaseIndentPattern"
    const val INDENT_NEXT_LINE_PATTERN: @NonNls String = "indentNextLinePattern"
    const val UNINDENTED_LINE_PATTERN: @NonNls String = "unIndentedLinePattern"

    // we should depend on intellij util classes as little as possible
    val DEFAULT_HIGHLIGHTING_BRACE_PAIRS: Set<TextMateBracePair> = setOf(
      TextMateBracePair("[", "]"),
      TextMateBracePair("{", "}"),
      TextMateBracePair("(", ")"))


    // we should depend on intellij util classes as little as possible
    val DEFAULT_SMART_TYPING_BRACE_PAIRS: Set<TextMateAutoClosingPair> = setOf(
      TextMateAutoClosingPair("\"", "\"", 0),
      TextMateAutoClosingPair("'", "'", 0),
      TextMateAutoClosingPair("[", "]", 0),
      TextMateAutoClosingPair("{", "}", 0),
      TextMateAutoClosingPair("(", ")", 0))
  }
}