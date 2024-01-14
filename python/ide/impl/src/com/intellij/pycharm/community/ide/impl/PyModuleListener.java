// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import org.jetbrains.annotations.NotNull;


public class PyModuleListener implements ModuleListener {
  @Override
  public void beforeModuleRemoved(@NotNull Project project, @NotNull Module module) {
    final RunManager runManager = RunManager.getInstance(project);
    for (RunnerAndConfigurationSettings configuration : runManager.getAllSettings()) {
      if (configuration.getConfiguration() instanceof AbstractPythonRunConfiguration) {
        final Module configModule = ((AbstractPythonRunConfiguration<?>)configuration.getConfiguration()).getModule();
        if (configModule == module) {
          runManager.removeConfiguration(configuration);
        }
      }
    }
  }
}
