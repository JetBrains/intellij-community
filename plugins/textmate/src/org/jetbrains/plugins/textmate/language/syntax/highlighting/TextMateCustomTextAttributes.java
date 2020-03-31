package org.jetbrains.plugins.textmate.language.syntax.highlighting;

import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;

public class TextMateCustomTextAttributes {
  private final TextAttributes myTextAttributes;
  private final double backgroundAlpha;

  public TextMateCustomTextAttributes(@NotNull TextAttributes attributes, double alpha) {
    myTextAttributes = attributes;
    backgroundAlpha = alpha;
  }

  public TextAttributes getTextAttributes() {
    return myTextAttributes;
  }

  public double getBackgroundAlpha() {
    return backgroundAlpha;
  }
}
