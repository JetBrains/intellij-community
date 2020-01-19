package org.jetbrains.plugins.textmate;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public class TextMateBundle extends DynamicBundle {
  private static final String BUNDLE = "messages.TextMateBundle";
  private static final TextMateBundle INSTANCE = new TextMateBundle();

  private TextMateBundle() { super(BUNDLE); }

  @NotNull
  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
    return INSTANCE.getMessage(key, params);
  }
}