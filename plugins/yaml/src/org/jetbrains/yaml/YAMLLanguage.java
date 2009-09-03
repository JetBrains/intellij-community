package org.jetbrains.yaml;

import com.intellij.lang.Language;

/**
 * @author oleg
 */
public class YAMLLanguage extends Language {
  public static final YAMLLanguage INSTANCE = new YAMLLanguage();

  private YAMLLanguage() {
    super("yaml");
  }

  @Override
  public String getDisplayName() {
    return "Yaml";
  }
}
