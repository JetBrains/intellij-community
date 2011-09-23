package com.jetbrains.python.run;

import com.intellij.execution.configuration.RunConfigurationExtension;
import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * @author traff
 */
public abstract class PythonRunConfigurationExtension extends RunConfigurationExtension<AbstractPythonRunConfiguration> {
  protected static final ExtensionPointName<PythonRunConfigurationExtension> EP_NAME = new ExtensionPointName<PythonRunConfigurationExtension>("Pythonid.runConfigurationExtension");
}
