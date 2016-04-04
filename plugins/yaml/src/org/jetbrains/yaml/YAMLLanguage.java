package org.jetbrains.yaml;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

/**
 * @author oleg
 */
public class YAMLLanguage extends Language {
  public static final YAMLLanguage INSTANCE = new YAMLLanguage();

  private YAMLLanguage() {
    super("yaml");
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "YAML";
  }
}
