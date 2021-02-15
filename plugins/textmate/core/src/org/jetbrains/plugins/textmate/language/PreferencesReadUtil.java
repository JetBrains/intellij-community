package org.jetbrains.plugins.textmate.language;

import com.intellij.util.containers.Interner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.language.preferences.TextMateBracePair;
import org.jetbrains.plugins.textmate.language.preferences.TextMateSnippet;
import org.jetbrains.plugins.textmate.plist.PListValue;
import org.jetbrains.plugins.textmate.plist.Plist;

import java.io.File;
import java.util.*;

public final class PreferencesReadUtil {
  /**
   * @param rootPlist
   * @return pair <scopeName, settingsPlist> or null if rootPlist doesn't contain 'settings' child
   * or scopeName is null or empty
   */
  @Nullable
  public static Map.Entry<String, Plist> retrieveSettingsPlist(Plist rootPlist) {
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
    return settingsValuePlist != null ? new AbstractMap.SimpleImmutableEntry<>(scopeName, settingsValuePlist) : null;
  }

  @Nullable
  public static Set<TextMateBracePair> readPairs(@Nullable PListValue pairsValue) {
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
        if (left.length() == 1 && right.length() == 1) {
          result.add(new TextMateBracePair(left.charAt(0), right.charAt(0)));
        }
      }
    }
    return result.isEmpty() ? Collections.emptySet() : result;
  }

  @Nullable
  private static TextMateSnippet loadTextMateSnippet(@NotNull Plist plist,
                                                     @NotNull String filePath,
                                                     @NotNull Interner<CharSequence> interner) {
    String name = plist.getPlistValue(Constants.NAME_KEY, "").getString();
    String key = plist.getPlistValue(Constants.TAB_TRIGGER_KEY, "").getString();
    String content = plist.getPlistValue(Constants.StringKey.CONTENT.value, "").getString();
    String scope = plist.getPlistValue(Constants.SCOPE_KEY, "").getString();
    String description = plist.getPlistValue(Constants.DESCRIPTION_KEY, "").getString(); //NON-NLS
    String uuid = plist.getPlistValue(Constants.UUID_KEY, "").getString();
    if (!key.isEmpty() && !content.isEmpty()) {
      if (name.isEmpty()) name = key;
      if (uuid.isEmpty()) uuid = filePath + ":" + name;
      return new TextMateSnippet(key, content, interner.intern(scope), name, description, uuid);
    }
    return null;
  }

  @NotNull
  public static <K, V> Map<K, V> compactMap(@NotNull Map<K, V> map) {
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

  @Nullable
  public static TextMateSnippet loadSnippet(@NotNull File snippetFile, @NotNull Plist plist, @NotNull Interner<CharSequence> interner) {
    return snippetFile.getName().endsWith("." + Constants.SUBLIME_SNIPPET_EXTENSION)
           ? null //not supported yet
           : loadTextMateSnippet(plist, snippetFile.getAbsolutePath(), interner);
  }
}
