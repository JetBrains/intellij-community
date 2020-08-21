// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.buildout.config;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.NlsSafe;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class BuildoutCfgFileType extends LanguageFileType {
  public static final BuildoutCfgFileType INSTANCE = new BuildoutCfgFileType();
  public static final @NlsSafe String DEFAULT_EXTENSION = "cfg";
  private static final @NlsSafe String NAME = "BuildoutCfg";
  private static final @NlsSafe String DESCRIPTION = "Buildout config";

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
  @NotNull
  public Icon getIcon() {
    return PythonIcons.Python.Buildout.Buildout;
  }
}

