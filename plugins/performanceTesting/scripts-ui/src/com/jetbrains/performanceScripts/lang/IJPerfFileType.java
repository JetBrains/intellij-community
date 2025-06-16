// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performanceScripts.lang;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.jetbrains.performanceScripts.PerformanceScriptsBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class IJPerfFileType extends LanguageFileType {

  public static final IJPerfFileType INSTANCE = new IJPerfFileType();
  public static final String DEFAULT_EXTENSION = "ijperf";

  private IJPerfFileType() {super(IJPerfLanguage.INSTANCE);}

  @Override
  public @NotNull String getName() {
    return "IntegrationPerformanceTest";
  }

  @Override
  public @NotNull String getDisplayName() {
    return PerformanceScriptsBundle.message("filetype.ijperformance.test.display.name");
  }

  @Override
  public @NotNull String getDescription() {
    return PerformanceScriptsBundle.message("filetype.ijperformance.test.description");
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Text;
  }
}
