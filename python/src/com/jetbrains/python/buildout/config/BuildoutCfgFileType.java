// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.buildout.config;

import com.intellij.openapi.fileTypes.LanguageFileType;
import icons.PythonIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class BuildoutCfgFileType extends LanguageFileType {
  public static final BuildoutCfgFileType INSTANCE = new BuildoutCfgFileType();
  @NonNls public static final String DEFAULT_EXTENSION = "cfg";
  @NonNls private static final String NAME = "BuildoutCfg";
  @NonNls private static final String DESCRIPTION = "Buildout config";

  private BuildoutCfgFileType() {
    super(BuildoutCfgLanguage.INSTANCE);
  }

  @Override
  @NotNull
  public String getName() {
    return NAME;
  }

  @Override
  @NotNull
  public String getDescription() {
    return DESCRIPTION;
  }

  @Override
  @NotNull
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  @Nullable
  public Icon getIcon() {
    return PythonIcons.Python.Buildout.Buildout;
  }
}

