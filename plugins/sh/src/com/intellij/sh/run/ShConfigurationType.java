// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.run;

import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.SimpleConfigurationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.sh.ShLanguage;
import com.intellij.util.EnvironmentUtil;
import icons.SHIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShConfigurationType extends SimpleConfigurationType {
  public ShConfigurationType() {
    super("ShConfigurationType", ShLanguage.INSTANCE.getID(), ShLanguage.INSTANCE.getID() + " configuration",
          NotNullLazyValue.createValue(() -> SHIcons.ShFile));
  }

  @NotNull
  @Override
  public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
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

  @Nullable
  public static String getDefaultShell() {
    return EnvironmentUtil.getValue("SHELL");
  }
}