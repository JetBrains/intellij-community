package org.jetbrains.plugins.textmate.language.preferences;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.plist.PListValue;
import org.jetbrains.plugins.textmate.plist.Plist;

import java.util.Map;

public final class TextMateTextAttributes {
  private final String foreground;
  private final String background;
  private final FontStyle fontStyle;

  public TextMateTextAttributes(@Nullable String foreground, @Nullable String background, @NotNull FontStyle style) {
    this.foreground = foreground;
    this.background = background;
    fontStyle = style;
  }

  public @Nullable String getForeground() {
    return foreground;
  }

  public @Nullable String getBackground() {
    return background;
  }

  public @NotNull FontStyle getFontStyle() {
    return fontStyle;
  }

  public static @Nullable TextMateTextAttributes fromPlist(@NotNull Plist settingsPlist) {
    boolean empty = true;
    String foreground = null;
    String background = null;
    FontStyle fontStyle = FontStyle.PLAIN;

    for (Map.Entry<String, PListValue> entry : settingsPlist.entries()) {
      final String propertyName = entry.getKey();
      final String value = entry.getValue().getString();
      if (Constants.FOREGROUND_KEY.equalsIgnoreCase(propertyName)) {
        foreground = value;
        empty = false;
      }
      else if (Constants.FONT_STYLE_KEY.equalsIgnoreCase(propertyName)) {
        if (Constants.ITALIC_FONT_STYLE.equalsIgnoreCase(value)) {
          fontStyle = FontStyle.ITALIC;
          empty = false;
        }
        else if (Constants.BOLD_FONT_STYLE.equalsIgnoreCase(value)) {
          fontStyle = FontStyle.BOLD;
          empty = false;
        }
        else if (Constants.UNDERLINE_FONT_STYLE.equalsIgnoreCase(value)) {
          fontStyle = FontStyle.UNDERLINE;
          empty = false;
        }
        else {
          fontStyle = FontStyle.PLAIN;
          empty = false;
        }
      }
      else if (Constants.BACKGROUND_KEY.equalsIgnoreCase(propertyName)) {
        background = value;
        empty = false;
      }
    }
    return empty ? null : new TextMateTextAttributes(foreground, background, fontStyle);
  }

  public enum FontStyle {
    PLAIN, ITALIC, BOLD, UNDERLINE
  }
}
