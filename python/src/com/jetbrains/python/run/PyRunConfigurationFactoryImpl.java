// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyRunConfigurationFactoryImpl extends PyRunConfigurationFactory {
  @Override
  public PythonRunConfigurationParams createPythonScriptRunConfiguration(Module module, String scriptName, boolean singleton) {
    RunnerAndConfigurationSettings settings = createRunConfiguration(module, PythonConfigurationType.getInstance().getFactory());
    settings.setSingleton(singleton);
    PythonRunConfigurationParams configuration = (PythonRunConfigurationParams)settings.getConfiguration();
    configuration.setScriptName(scriptName);
    return configuration;
  }

  @Override
  public RunnerAndConfigurationSettings createRunConfiguration(Module module, ConfigurationFactory factory) {
    final Project project = module.getProject();
    final RunManager runManager = RunManager.getInstance(project);
    final RunnerAndConfigurationSettings settings = createConfigurationSettings(factory, module);
    runManager.addConfiguration(settings);
    runManager.setSelectedConfiguration(settings);
    return settings;
  }

  private static RunnerAndConfigurationSettings createConfigurationSettings(ConfigurationFactory factory, @NotNull final Module module) {
    final RunnerAndConfigurationSettings settings =
      RunManager.getInstance(module.getProject()).createRunConfiguration(module.getName(), factory);
    ModuleBasedConfiguration configuration = (ModuleBasedConfiguration) settings.getConfiguration();
    configuration.setModule(module);
    return settings;
  }
}
