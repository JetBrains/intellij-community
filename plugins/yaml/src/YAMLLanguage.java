// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

public final class YAMLLanguage extends Language {
  public static final YAMLLanguage INSTANCE = new YAMLLanguage();

  private YAMLLanguage() {
    super("yaml", "application/x-yaml", "application/yaml", "text/yaml", "text/x-yaml");
  }

  @Override
  public @NotNull String getDisplayName() {
    return "YAML";
  }
}
