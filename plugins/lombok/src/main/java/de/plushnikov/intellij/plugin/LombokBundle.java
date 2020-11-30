package de.plushnikov.intellij.plugin;

import com.intellij.AbstractBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

/**
 * {@link ResourceBundle}/localization utils for the lombok plugin.
 */
public final class LombokBundle {
  /**
   * The {@link ResourceBundle} path.
   */
  @NonNls
  private static final String BUNDLE_NAME = "messages.LombokBundle";

  /**
   * The {@link ResourceBundle} instance.
   */
  private static final ResourceBundle BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);

  private LombokBundle() {
  }

  public static @Nls String message(@PropertyKey(resourceBundle = BUNDLE_NAME) String key, Object... params) {
    return AbstractBundle.message(BUNDLE, key, params);
  }
}
