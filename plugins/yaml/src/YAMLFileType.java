// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class YAMLFileType extends LanguageFileType {
  public static final YAMLFileType YML = new YAMLFileType();
  public static final @NonNls String DEFAULT_EXTENSION = "yml";

  private YAMLFileType() {
    super(YAMLLanguage.INSTANCE);
  }

  @Override
  public @NotNull String getName() {
    return "YAML";
  }

  @Override
  public @NotNull String getDescription() {
    return YAMLBundle.message("filetype.yaml.description");
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Yaml;
  }
}

