// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;

/**
 * @author yole
 */
public abstract class PyRunConfigurationFactory {
  public static PyRunConfigurationFactory getInstance() {
    return ServiceManager.getService(PyRunConfigurationFactory.class);
  }

  /**
   * Creates a run configuration to run a specified Python script.
   *
   * @param module the module in the context of which the script is run.
   * @param scriptName the path to the script file.
   * @return the settings of the created run configuration.
   */
  public abstract PythonRunConfigurationParams createPythonScriptRunConfiguration(Module module, String scriptName);

  public abstract RunnerAndConfigurationSettings createRunConfiguration(Module module, ConfigurationFactory factory);
}
