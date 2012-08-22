package com.jetbrains.python.run;

import com.intellij.execution.RunManagerEx;
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
    final RunManagerEx runManagerEx = RunManagerEx.getInstanceEx(project);
    final RunnerAndConfigurationSettings settings = createConfigurationSettings(factory, module);
    runManagerEx.addConfiguration(settings, false);
    runManagerEx.setSelectedConfiguration(settings);
    return settings;
  }

  private static RunnerAndConfigurationSettings createConfigurationSettings(ConfigurationFactory factory, @NotNull final Module module) {
    final RunnerAndConfigurationSettings settings =
      RunManagerEx.getInstanceEx(module.getProject()).createConfiguration(module.getName(), factory);
    ModuleBasedConfiguration configuration = (ModuleBasedConfiguration) settings.getConfiguration();
    configuration.setModule(module);
    return settings;
  }
}
