/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.buildout.config;

import com.intellij.openapi.fileTypes.LanguageFileType;
import icons.PythonIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author traff
 */
public class BuildoutCfgFileType extends LanguageFileType {
  public static final BuildoutCfgFileType INSTANCE = new BuildoutCfgFileType();
  @NonNls public static final String DEFAULT_EXTENSION = "cfg";
  @NonNls private static final String NAME = "BuildoutCfg";
  @NonNls private static final String DESCRIPTION = "Buildout config files";

  private BuildoutCfgFileType() {
    super(BuildoutCfgLanguage.INSTANCE);
  }

  @NotNull
  public String getName() {
    return NAME;
  }

  @NotNull
  public String getDescription() {
    return DESCRIPTION;
  }

  @NotNull
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Nullable
  public Icon getIcon() {
    return PythonIcons.Python.Buildout.Buildout;
  }
}

