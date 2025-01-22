// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.sh.run;

import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.SimpleConfigurationType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.platform.eel.EelDescriptor;
import com.intellij.platform.eel.EelPlatform;
import com.intellij.platform.eel.provider.LocalEelDescriptor;
import com.intellij.sh.ShBundle;
import com.intellij.sh.ShLanguage;
import org.jetbrains.annotations.NotNull;

import static com.intellij.platform.eel.provider.EelProviderUtil.getEelDescriptor;
import static com.intellij.platform.eel.provider.EelProviderUtil.upgradeBlocking;
import static com.intellij.platform.eel.provider.utils.EelPathUtils.getNioPath;
import static com.intellij.platform.eel.provider.utils.EelUtilsKt.fetchLoginShellEnvVariablesBlocking;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.isExecutable;

public final class ShConfigurationType extends SimpleConfigurationType {
  ShConfigurationType() {
    super("ShConfigurationType", ShLanguage.INSTANCE.getID(),
          ShBundle.message("sh.run.configuration.description.0.configuration", ShLanguage.INSTANCE.getID()),
          NotNullLazyValue.lazy(() -> AllIcons.Nodes.Console));
  }

  @Override
  public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project) {
    ShRunConfiguration configuration = new ShRunConfiguration(project, this, ShLanguage.INSTANCE.getID());
    String defaultShell = getDefaultShell(project);
    configuration.setInterpreterPath(defaultShell);
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

  public static @NotNull String getDefaultShell(@NotNull Project project) {
    final var shellPathProvider = project.getService(ShDefaultShellPathProvider.class);
    final var eelDescriptor = project.isDefault() ? LocalEelDescriptor.INSTANCE : getEelDescriptor(project);

    if (shellPathProvider != null
        && eelDescriptor == LocalEelDescriptor.INSTANCE) { // todo: remove this check when terminal will be migrated to eel
      return shellPathProvider.getDefaultShell();
    }
    else {
      return trivialDefaultShellDetection(eelDescriptor);
    }
  }

  private static @NotNull String trivialDefaultShellDetection(final @NotNull EelDescriptor eelDescriptor) {
    final var eel = upgradeBlocking(eelDescriptor);
    final var shell = fetchLoginShellEnvVariablesBlocking(eel.getExec()).get("SHELL");

    if (shell != null && isExecutable(getNioPath(shell, eelDescriptor))) {
      return shell;
    }
    if (eel.getPlatform() instanceof EelPlatform.Linux) {
      String bashPath = "/bin/bash";
      if (exists(getNioPath(bashPath, eelDescriptor))) {
        return bashPath;
      }
      return "/bin/sh";
    }
    return "powershell.exe";
  }
}