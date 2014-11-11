/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
   * @param singleton if true, the "Check no other instances are running" option will be set for the run configuration.
   * @return the settings of the created run configuration.
   */
  public abstract PythonRunConfigurationParams createPythonScriptRunConfiguration(Module module, String scriptName, boolean singleton);

  public abstract RunnerAndConfigurationSettings createRunConfiguration(Module module, ConfigurationFactory factory);
}
