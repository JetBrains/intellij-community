package com.jetbrains.python.run;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

public interface AbstractPythonRunConfigurationParams {
  String getInterpreterOptions();

  void setInterpreterOptions(String interpreterOptions);

  String getWorkingDirectory();

  void setWorkingDirectory(String workingDirectory);

  @Nullable
  String getSdkHome();

  void setSdkHome(String sdkHome);

  boolean isPassParentEnvs();

  void setPassParentEnvs(boolean passParentEnvs);

  Map<String, String> getEnvs();

  void setEnvs(final Map<String, String> envs);
}
