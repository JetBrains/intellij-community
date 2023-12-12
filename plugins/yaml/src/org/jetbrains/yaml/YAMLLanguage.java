// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

public final class YAMLLanguage extends Language {
  public static final YAMLLanguage INSTANCE = new YAMLLanguage();

  private YAMLLanguage() {
    super("yaml", "application/x-yaml", "application/yaml", "text/yaml", "text/x-yaml");
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "YAML";
  }
}
