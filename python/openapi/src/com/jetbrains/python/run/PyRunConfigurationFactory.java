// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;

/**
 * @author yole
 */
public abstract class PyRunConfigurationFactory {
  public static PyRunConfigurationFactory getInstance() {
    return ApplicationManager.getApplication().getService(PyRunConfigurationFactory.class);
  }

  /**
   * Creates a run configuration to run a specified Python script.
   *
   * @param module the module in the context of which the script is run.
   * @param scriptName the path to the script file.
   * @return the settings of the created run configuration.
   */
  public abstract PythonRunConfigurationParams createPythonScriptRunConfiguration(Module module, String scriptName);
}
