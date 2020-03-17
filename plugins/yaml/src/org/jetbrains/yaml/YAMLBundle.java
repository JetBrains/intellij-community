package org.jetbrains.yaml;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public class YAMLBundle extends DynamicBundle {
  @NonNls private static final String BUNDLE = "messages.YAMLBundle";
  private static final YAMLBundle INSTANCE = new YAMLBundle();

  private YAMLBundle() { super(BUNDLE); }

  @NotNull
  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
    return INSTANCE.getMessage(key, params);
  }
}