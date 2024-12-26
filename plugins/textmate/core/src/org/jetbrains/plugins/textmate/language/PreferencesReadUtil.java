package org.jetbrains.plugins.textmate.language;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.language.preferences.IndentationRules;
import org.jetbrains.plugins.textmate.language.preferences.ShellVariablesRegistry;
import org.jetbrains.plugins.textmate.language.preferences.TextMateBracePair;
import org.jetbrains.plugins.textmate.language.preferences.TextMateShellVariable;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;
import org.jetbrains.plugins.textmate.plist.PListValue;
import org.jetbrains.plugins.textmate.plist.Plist;

import java.util.*;

public final class PreferencesReadUtil {
  /**
   * @return pair <scopeName, settingsPlist> or null if rootPlist doesn't contain 'settings' child
   * or scopeName is null or empty
   */
  public static @Nullable Map.Entry<String, Plist> retrieveSettingsPlist(Plist rootPlist) {
    String scopeName = null;
    Plist settingsValuePlist = null;
    final PListValue value = rootPlist.getPlistValue(Constants.SCOPE_KEY);
    if (value != null) {
      scopeName = value.getString();
      final PListValue settingsValue = rootPlist.getPlistValue(Constants.SETTINGS_KEY);
      if (scopeName != null && !scopeName.isEmpty() && settingsValue != null) {
        settingsValuePlist = settingsValue.getPlist();
      }
    }
    return settingsValuePlist != null ? Map.entry(scopeName, settingsValuePlist) : null;
  }

  public static @Nullable Set<TextMateBracePair> readPairs(@Nullable PListValue pairsValue) {
    if (pairsValue == null) {
      return null;
    }

    Set<TextMateBracePair> result = new HashSet<>();
    List<PListValue> pairs = pairsValue.getArray();
    for (PListValue pair : pairs) {
      List<PListValue> chars = pair.getArray();
      if (chars.size() == 2) {
        String left = chars.get(0).getString();
        String right = chars.get(1).getString();
        if (!left.isEmpty() && !right.isEmpty()) {
          result.add(new TextMateBracePair(left, right));
        }
      }
    }
    return result.isEmpty() ? Collections.emptySet() : result;
  }

  public static @NotNull <K, V> Map<K, V> compactMap(@NotNull Map<K, V> map) {
    if (map.isEmpty()) {
      return Collections.emptyMap();
    }
    if (map.size() == 1) {
      Map.Entry<K, V> singleEntry = map.entrySet().iterator().next();
      return Collections.singletonMap(singleEntry.getKey(), singleEntry.getValue());
    }
    if (!(map instanceof HashMap)) {
      return map;
    }
    HashMap<K, V> result = new HashMap<>(map.size(), 1.0f);
    result.putAll(map);
    return result;
  }

  private PreferencesReadUtil() {
  }

  private static @Nullable String getPattern(@NotNull String name, @NotNull Plist from) {
    final PListValue value = from.getPlistValue(name);
    if (value == null) return null;
    return value.getString();
  }

  public static @NotNull IndentationRules loadIndentationRules(@NotNull Plist plist) {
    final PListValue rulesValue = plist.getPlistValue(Constants.INDENTATION_RULES);
    if (rulesValue == null) return IndentationRules.empty();
    final Plist rules = rulesValue.getPlist();
    return new IndentationRules(
      getPattern(Constants.INCREASE_INDENT_PATTERN, rules),
      getPattern(Constants.DECREASE_INDENT_PATTERN, rules),
      getPattern(Constants.INDENT_NEXT_LINE_PATTERN, rules),
      getPattern(Constants.UNINDENTED_LINE_PATTERN, rules)
    );
  }

  public static @NotNull TextMateCommentPrefixes readCommentPrefixes(final @NotNull ShellVariablesRegistry registry,
                                                                     final @NotNull TextMateScope scope) {

    String lineCommentPrefix = null;
    TextMateBlockCommentPair blockCommentPair = null;
    int index = 1;
    while (lineCommentPrefix == null || blockCommentPair == null) {
      String variableSuffix = index > 1 ? "_" + index : "";
      TextMateShellVariable start = registry.getVariableValue(Constants.COMMENT_START_VARIABLE + variableSuffix, scope);
      TextMateShellVariable end = registry.getVariableValue(Constants.COMMENT_END_VARIABLE + variableSuffix, scope);

      index++;

      if (start == null) break;
      if ((end == null || !end.scopeName.equals(start.scopeName)) && lineCommentPrefix == null) {
        lineCommentPrefix = start.value;
      }
      if ((end != null && end.scopeName.equals(start.scopeName)) && blockCommentPair == null) {
        blockCommentPair = new TextMateBlockCommentPair(start.value, end.value);
      }
    }

    return new TextMateCommentPrefixes(lineCommentPrefix, blockCommentPair);
  }
}