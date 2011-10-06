package com.jetbrains.python.run;

import com.intellij.execution.configuration.RunConfigurationExtensionBase;
import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * @author traff
 */
public abstract class PythonRunConfigurationExtension extends RunConfigurationExtensionBase<AbstractPythonRunConfiguration> {
  protected static final ExtensionPointName<PythonRunConfigurationExtension> EP_NAME = new ExtensionPointName<PythonRunConfigurationExtension>("Pythonid.runConfigurationExtension");
}
