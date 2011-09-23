package com.jetbrains.python.run;

import com.intellij.execution.configuration.RunConfigurationExtensionsManager;
import com.intellij.openapi.components.ServiceManager;

/**
 * @author traff
 */
public class PythonRunConfigurationExtensionsManager
  extends RunConfigurationExtensionsManager<AbstractPythonRunConfiguration, PythonRunConfigurationExtension> {

  public PythonRunConfigurationExtensionsManager() {
    super(PythonRunConfigurationExtension.EP_NAME);
  }

  public static PythonRunConfigurationExtensionsManager getInstance() {
    return ServiceManager.getService(PythonRunConfigurationExtensionsManager.class);
  }
}
