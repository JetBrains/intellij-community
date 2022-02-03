// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.sh.run;

import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.SimpleConfigurationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.sh.SHIcons;
import com.intellij.sh.ShBundle;
import com.intellij.sh.ShLanguage;
import com.intellij.util.EnvironmentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ShConfigurationType extends SimpleConfigurationType {
  ShConfigurationType() {
    super("ShConfigurationType", ShLanguage.INSTANCE.getID(),
          ShBundle.message("sh.run.configuration.description.0.configuration", ShLanguage.INSTANCE.getID()),
          NotNullLazyValue.lazy(() -> SHIcons.ShFile));
  }

  @Override
  public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project) {
    ShRunConfiguration configuration = new ShRunConfiguration(project, this, ShLanguage.INSTANCE.getID());
    String defaultShell = getDefaultShell();
    if (defaultShell != null) {
      configuration.setInterpreterPath(defaultShell);
    }
    String projectPath = project.getBasePath();
    if (projectPath != null) {
      configuration.setScriptWorkingDirectory(projectPath);
    }
    return configuration;
  }

  public static ShConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(ShConfigurationType.class);
  }

  @Override
  public boolean isEditableInDumbMode() {
    return true;
  }

  public static @Nullable String getDefaultShell() {
    return EnvironmentUtil.getValue("SHELL");
  }
}