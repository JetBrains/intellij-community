package org.jetbrains.plugins.textmate;

import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.textmate.language.preferences.TextMateBracePair;

import java.util.Collection;

public interface Constants {
  // KEYS
  @NonNls String NAME_KEY = "name";
  @NonNls String VALUE_KEY = "value";
  @NonNls String CONTENT_KEY = "content";
  @NonNls String TAB_TRIGGER_KEY = "tabTrigger";
  @NonNls String CONTENT_NAME_KEY = "contentName";
  @NonNls String FILE_TYPES_KEY = "fileTypes";
  @NonNls String INCLUDE_KEY = "include";
  @NonNls String REPOSITORY_KEY = "repository";
  @NonNls String PATTERNS_KEY = "patterns";
  @NonNls String INJECTIONS_KEY = "injections";
  @NonNls String SCOPE_KEY = "scope";
  @NonNls String UUID_KEY = "uuid";
  @NonNls String SCOPE_NAME_KEY = "scopeName";
  @NonNls String BEGIN_KEY = "begin";
  @NonNls String CAPTURES_KEY = "captures";
  @NonNls String BEGIN_CAPTURES_KEY = "beginCaptures";
  @NonNls String END_CAPTURES_KEY = "endCaptures";
  @NonNls String MATCH_KEY = "match";
  @NonNls String END_KEY = "end";
  @NonNls String WHILE_KEY = "while";
  @NonNls String FOREGROUND_KEY = "foreground";
  @NonNls String FONT_STYLE_KEY = "fontStyle";
  @NonNls String BACKGROUND_KEY = "background";
  @NonNls String SHELL_VARIABLES_KEY = "shellVariables";
  @NonNls String DESCRIPTION_KEY = "description";

  String[] STRING_KEY_NAMES = new String[]{CONTENT_KEY, NAME_KEY, CONTENT_NAME_KEY, WHILE_KEY, END_KEY, SCOPE_NAME_KEY};
  String[] REGEX_KEY_NAMES = new String[]{"firstLineMatch", "foldingStartMarker", MATCH_KEY, BEGIN_KEY};
  String[] DICT_KEY_NAMES = new String[]{CAPTURES_KEY, BEGIN_CAPTURES_KEY, END_CAPTURES_KEY};

  // VALUES
  @NonNls String DEFAULT_SCOPE_NAME = "default";
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
  @NonNls String HIGHLIGHTING_PAIRS_KEY = "highlightingPairs";
  @NonNls String SMART_TYPING_PAIRS_KEY = "smartTypingPairs";

  Collection<TextMateBracePair> DEFAULT_HIGHLIGHTING_BRACE_PAIRS = ImmutableSet.of(
    new TextMateBracePair('[', ']'),
    new TextMateBracePair('{', '}'),
    new TextMateBracePair('(', ')')
  );

  Collection<TextMateBracePair> DEFAULT_SMART_TYPING_BRACE_PAIRS = ImmutableSet.of(
    new TextMateBracePair('"', '"'),
    new TextMateBracePair('\'', '\''),
    new TextMateBracePair('[', ']'),
    new TextMateBracePair('{', '}'),
    new TextMateBracePair('(', ')')
  );
}
