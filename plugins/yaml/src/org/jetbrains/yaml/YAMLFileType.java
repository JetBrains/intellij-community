// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class YAMLFileType extends LanguageFileType {
  public static final YAMLFileType YML = new YAMLFileType();
  @NonNls public static final String DEFAULT_EXTENSION = "yml";

  private YAMLFileType() {
    super(YAMLLanguage.INSTANCE);
  }

  @Override
  @NotNull
  public String getName() {
    return "YAML";
  }

  @Override
  @NotNull
  public String getDescription() {
    return YAMLBundle.message("filetype.description.yaml");
  }

  @Override
  @NotNull
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  @Nullable
  public Icon getIcon() {
    return AllIcons.FileTypes.Yaml;
  }
}

