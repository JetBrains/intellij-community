package org.jetbrains.plugins.textmate.language.syntax.highlighting;

import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.language.preferences.TextMateTextAttributes;

import java.awt.*;

public class TextMateTextAttributesAdapter {
  private final TextAttributes myTextAttributes;
  private final double myForegroundAlpha;
  private final double myBackgroundAlpha;
  private final CharSequence myScopeName;

  public TextMateTextAttributesAdapter(@NotNull CharSequence scopeName,
                                       @NotNull TextMateTextAttributes attributes) {
    myScopeName = scopeName;
    myTextAttributes = new TextAttributes();

    Pair<Color, Double> foreground = getColor(attributes.getForeground());
    myTextAttributes.setForegroundColor(foreground.first);
    myForegroundAlpha = foreground.second;

    Pair<Color, Double> background = getColor(attributes.getBackground());
    myTextAttributes.setBackgroundColor(background.first);
    myBackgroundAlpha = background.second;

    switch (attributes.getFontStyle()) {
      case PLAIN -> myTextAttributes.setFontType(Font.PLAIN);
      case ITALIC -> myTextAttributes.setFontType(Font.ITALIC);
      case BOLD -> myTextAttributes.setFontType(Font.BOLD);
      case UNDERLINE -> {
        Color foregroundColor = myTextAttributes.getForegroundColor();
        Color effectColor;
        if (foregroundColor != null) {
          effectColor = foregroundColor;
        }
        else {
          TextAttributes defaultAttributes = HighlighterColors.TEXT.getDefaultAttributes();
          effectColor = defaultAttributes != null ? defaultAttributes.getForegroundColor() : null;
        }
        myTextAttributes.setEffectColor(effectColor);
        myTextAttributes.setEffectType(EffectType.LINE_UNDERSCORE);
      }
    }
  }

  public @NotNull TextAttributesKey getTextAttributesKey(@NotNull TextMateTheme textMateTheme) {
    Color defaultBackground = textMateTheme.getDefaultBackground();
    Color mixedBackground = mixBackground(defaultBackground, myTextAttributes.getBackgroundColor(), myBackgroundAlpha);
    Color mixedForeground = mixBackground(defaultBackground, myTextAttributes.getForegroundColor(), myForegroundAlpha);

    if (mixedBackground == null && mixedForeground == null) {
      return TextAttributesKey.createTextAttributesKey("TextMateCustomRule_" + myScopeName, myTextAttributes);
    }

    TextAttributes result = new TextAttributes();
    result.copyFrom(myTextAttributes);
    if (mixedForeground != null) {
      myTextAttributes.setForegroundColor(mixedForeground);
    }
    if (mixedBackground != null) {
      myTextAttributes.setBackgroundColor(mixedBackground);
    }
    return TextAttributesKey.createTextAttributesKey("TextMateCustomRule_" + TextMateTheme.INSTANCE.getName() + myScopeName, result);
  }

  @Nullable
  private static Color mixBackground(@Nullable Color color, @Nullable Color defaultBackground, double alpha) {
    if (color == null || defaultBackground == null || alpha < 0) {
      return null;
    }
    return ColorUtil.mix(defaultBackground, color, alpha);
  }

  public Pair<Color, Double> getColor(@Nullable String s) {
    if (s == null) {
      return Pair.create(null, -1.0);
    }
    int startOffset = StringUtil.startsWithChar(s, '#') ? 1 : 0;
    Color color = ColorUtil.fromHex(s.substring(startOffset, startOffset + 6), null);
    double alpha = s.length() > 7 ? parseAlpha(s.substring(startOffset + 6)) : -1;
    return Pair.create(color, alpha);
  }

  private static double parseAlpha(String string) {
    try {
      return Integer.parseInt(string, 16) / 256.0;
    }
    catch (NumberFormatException e) {
      return -1;
    }
  }
}
