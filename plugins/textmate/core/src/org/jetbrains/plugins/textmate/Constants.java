package org.jetbrains.plugins.textmate;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.language.preferences.TextMateAutoClosingPair;
import org.jetbrains.plugins.textmate.language.preferences.TextMateBracePair;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public interface Constants {
  // KEYS
  @NonNls String NAME_KEY = "name";
  @NonNls String VALUE_KEY = "value";
  @NonNls String TAB_TRIGGER_KEY = "tabTrigger";
  @NonNls String FILE_TYPES_KEY = "fileTypes";

  @NonNls String FIRST_LINE_MATCH = "firstLineMatch";
  @NonNls String INCLUDE_KEY = "include";
  @NonNls String REPOSITORY_KEY = "repository";
  @NonNls String PATTERNS_KEY = "patterns";
  @NonNls String INJECTIONS_KEY = "injections";
  @NonNls String SCOPE_KEY = "scope";
  @NonNls String UUID_KEY = "uuid";
  @NonNls String FOREGROUND_KEY = "foreground";
  @NonNls String FONT_STYLE_KEY = "fontStyle";
  @NonNls String BACKGROUND_KEY = "background";
  @NonNls String SHELL_VARIABLES_KEY = "shellVariables";
  @NonNls String DESCRIPTION_KEY = "description";

  enum StringKey {
    CONTENT("content"),
    NAME("name"),
    CONTENT_NAME("contentName"),
    WHILE("while"),
    END("end"),
    SCOPE_NAME("scopeName"),
    MATCH("match"),
    BEGIN("begin");

    public final String value;

    StringKey(String name) {
      value = name;
    }

    @Nullable
    public static StringKey fromName(@NotNull String name) {
      for (StringKey v : values()) {
        if (v.value.equals(name)) {
          return v;
        }
      }
      return null;
    }
  }

  enum CaptureKey {
    CAPTURES("captures"),
    BEGIN_CAPTURES("beginCaptures"),
    END_CAPTURES("endCaptures");

    public final String value;

    CaptureKey(String name) {
      value = name;
    }

    @Nullable
    public static CaptureKey fromName(@NotNull String name) {
      for (CaptureKey v : values()) {
        if (v.value.equals(name)) {
          return v;
        }
      }
      return null;
    }
  }

  // VALUES
  @NonNls String INCLUDE_SELF_VALUE = "$self";
  @NonNls String INCLUDE_BASE_VALUE = "$base";
  @NonNls String BOLD_FONT_STYLE = "bold";
  @NonNls String ITALIC_FONT_STYLE = "italic";
  @NonNls String UNDERLINE_FONT_STYLE = "underline";
  @NonNls String SETTINGS_KEY = "settings";

  // OTHER
  @NonNls String BUNDLE_INFO_PLIST_NAME = "info.plist";
  @NonNls String PACKAGE_JSON_NAME = "package.json";
  @NonNls String TEXTMATE_SNIPPET_EXTENSION = "tmsnippet";
  @NonNls String SUBLIME_SNIPPET_EXTENSION = "sublime-snippet";

  // SHELL VARIABLES
  @NonNls String COMMENT_START_VARIABLE = "TM_COMMENT_START";
  @NonNls String COMMENT_END_VARIABLE = "TM_COMMENT_END";


  // PREFERENCES
  @NonNls String HIGHLIGHTING_PAIRS_KEY = "highlightPairs";
  @NonNls String SMART_TYPING_PAIRS_KEY = "smartTypingPairs";
  @NonNls String INDENTATION_RULES = "indentationRules";

  // INDENTATION PATTERNS
  @NonNls String INCREASE_INDENT_PATTERN = "increaseIndentPattern";
  @NonNls String DECREASE_INDENT_PATTERN = "decreaseIndentPattern";
  @NonNls String INDENT_NEXT_LINE_PATTERN = "indentNextLinePattern";
  @NonNls String UNINDENTED_LINE_PATTERN = "unIndentedLinePattern";

  // we should depend on intellij util classes as little as possible
  @SuppressWarnings("SSBasedInspection")
  Set<TextMateBracePair> DEFAULT_HIGHLIGHTING_BRACE_PAIRS =
    new HashSet<>(Arrays.asList(new TextMateBracePair("[", "]"),
                                new TextMateBracePair("{", "}"),
                                new TextMateBracePair("(", ")")));

  // we should depend on intellij util classes as little as possible
  @SuppressWarnings("SSBasedInspection")
  Set<TextMateAutoClosingPair> DEFAULT_SMART_TYPING_BRACE_PAIRS =
    new HashSet<>(Arrays.asList(new TextMateAutoClosingPair("\"", "\"", null),
                                new TextMateAutoClosingPair("'", "'", null),
                                new TextMateAutoClosingPair("[", "]", null),
                                new TextMateAutoClosingPair("{", "}", null),
                                new TextMateAutoClosingPair("(", ")", null)));
}