package org.jetbrains.plugins.textmate.language;

import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.editor.TextMateSnippet;
import org.jetbrains.plugins.textmate.language.preferences.TextMateBracePair;
import org.jetbrains.plugins.textmate.plist.PListValue;
import org.jetbrains.plugins.textmate.plist.Plist;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PreferencesReadUtil {
  /**
   * @param rootPlist
   * @return pair <scopeName, settingsPlist> or null if rootPlist doesn't contain 'settings' child
   * or scopeName is null or empty
   */
  @Nullable
  public static Pair<String, Plist> retrieveSettingsPlist(Plist rootPlist) {
    String scopeName = null;
    Plist settingsValuePlist = null;
    final PListValue value = rootPlist.getPlistValue(Constants.SCOPE_KEY);
    if (value != null) {
      scopeName = value.getString();
      final PListValue settingsValue = rootPlist.getPlistValue(Constants.SETTINGS_KEY);
      if (StringUtil.isNotEmpty(scopeName) && settingsValue != null) {
        settingsValuePlist = settingsValue.getPlist();
      }
    }
    return settingsValuePlist != null ? Pair.create(scopeName, settingsValuePlist) : null;
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
    return result;
  }

  /**
   * @param attributes      attributes to fill
   * @param settingsPlist   plist with text settings
   * @param backgroundColor
   * @return true if plist contains any text-presentation settings, false otherwise
   */
  public static boolean fillTextAttributes(TextAttributes attributes, Plist settingsPlist, @Nullable Color backgroundColor) {
    boolean result = false;
    for (Map.Entry<String, PListValue> entry : settingsPlist.entries()) {
      final String propertyName = entry.getKey();
      final String value = entry.getValue().getString();
      if (Constants.FOREGROUND_KEY.equalsIgnoreCase(propertyName)) {
        attributes.setForegroundColor(getColor(value, null));
        result = true;
      }
      else if (Constants.FONT_STYLE_KEY.equalsIgnoreCase(propertyName)) {
        if (Constants.ITALIC_FONT_STYLE.equalsIgnoreCase(value)) {
          attributes.setFontType(Font.ITALIC);
        }
        else if (Constants.BOLD_FONT_STYLE.equalsIgnoreCase(value)) {
          attributes.setFontType(Font.BOLD);
        }
        else if (Constants.UNDERLINE_FONT_STYLE.equalsIgnoreCase(value)) {
          Color foregroundColor = attributes.getForegroundColor();
          Color effectColor = foregroundColor != null
                              ? foregroundColor
                              : HighlighterColors.TEXT.getDefaultAttributes().getForegroundColor();
          attributes.setEffectColor(effectColor);
          attributes.setEffectType(EffectType.LINE_UNDERSCORE);
        }
        else {
          attributes.setFontType(Font.PLAIN);
        }
        result = true;
      }
      else if (Constants.BACKGROUND_KEY.equalsIgnoreCase(propertyName)) {
        attributes.setBackgroundColor(getColor(value, backgroundColor));
        result = true;
      }
    }
    return result;
  }

  @Nullable
  private static TextMateSnippet loadTextMateSnippet(@NotNull Plist plist, @NotNull String filePath) throws IOException {
    String name = plist.getPlistValue(Constants.NAME_KEY, "").getString();
    String key = plist.getPlistValue(Constants.TAB_TRIGGER_KEY, "").getString();
    String content = plist.getPlistValue(Constants.CONTENT_KEY, "").getString();
    String scope = plist.getPlistValue(Constants.SCOPE_KEY, "").getString();
    String description = plist.getPlistValue(Constants.DESCRIPTION_KEY, "").getString();
    String uuid = plist.getPlistValue(Constants.UUID_KEY, "").getString();
    if (!key.isEmpty() && !content.isEmpty()) {
      if (name.isEmpty()) name = key;
      if (uuid.isEmpty()) uuid = filePath + ":" + name;
      return new TextMateSnippet(key, content, scope, name, description, uuid);
    }
    return null;
  }

  @Nullable
  private static TextMateSnippet loadSublimeSnippet(@NotNull File file) {
    return null;
  }

  public static double getBackgroundAlpha(Plist settingsPlist) {
    final PListValue value = settingsPlist.getPlistValue(Constants.BACKGROUND_KEY);
    if (value != null) {
      String background = value.getString();
      if (background.length() > 7) {
        return parseAlpha(background.substring(StringUtil.startsWithChar(background, '#') ? 7 : 6));
      }
    }
    return -1;
  }

  private static Color getColor(@NotNull String value, @Nullable Color backgroundColor) {
    if (value.length() > 7) {
      int startOffset = StringUtil.startsWithChar(value, '#') ? 1 : 0;
      Color color = ColorUtil.fromHex(value.substring(startOffset, startOffset + 6), null);
      if (color != null && backgroundColor != null) {
        final double alpha = parseAlpha(value.substring(startOffset + 6));
        if (alpha > -1) {
          return ColorUtil.mix(backgroundColor, color, alpha);
        }
      }
      return color;
    }
    return ColorUtil.fromHex(value, null);
  }

  private static double parseAlpha(String string) {
    try {
      return Integer.parseInt(string, 16) / 256.0;
    }
    catch (NumberFormatException e) {
      return -1;
    }
  }

  private PreferencesReadUtil() {
  }

  @Nullable
  public static TextMateSnippet loadSnippet(@NotNull File snippetFile, @NotNull Plist plist) throws IOException {
    return FileUtilRt.extensionEquals(snippetFile.getName(), Constants.SUBLIME_SNIPPET_EXTENSION)
           ? loadSublimeSnippet(snippetFile)
           : loadTextMateSnippet(plist, snippetFile.getAbsolutePath());
  }
}
