package com.jetbrains.python.run;

/**
 * @author Leonid Shalupov
 */
public interface PythonRunConfigurationParams extends AbstractPythonRunConfigurationParams {
  String getScriptName();

  void setScriptName(String scriptName);

  String getScriptParameters();

  void setScriptParameters(String scriptParameters);
}
