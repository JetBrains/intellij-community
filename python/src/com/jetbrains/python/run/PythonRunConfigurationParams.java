package com.jetbrains.python.run;

/**
 * @author Leonid Shalupov
 */
public interface PythonRunConfigurationParams {
  AbstractPythonRunConfigurationParams getBaseParams();

  String getScriptName();

  void setScriptName(String scriptName);

  String getScriptParameters();

  void setScriptParameters(String scriptParameters);

  boolean isMultiprocessMode();

  void setMultiprocessMode(boolean multiprocess);
}
