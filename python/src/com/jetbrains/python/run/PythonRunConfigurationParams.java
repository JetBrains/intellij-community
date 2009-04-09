package com.jetbrains.python.run;

public interface PythonRunConfigurationParams extends AbstractPythonRunConfigurationParams {
  String getScriptName();

  void setScriptName(String scriptName);

  String getScriptParameters();

  void setScriptParameters(String scriptParameters);
}
