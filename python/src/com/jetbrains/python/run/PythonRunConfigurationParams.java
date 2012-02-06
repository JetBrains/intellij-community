package com.jetbrains.python.run;

import org.jetbrains.annotations.Nullable;

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

  @Nullable
  String getRemoteDebugConfiguration();

  void setRemoteDebugConfiguration(String name);
}

