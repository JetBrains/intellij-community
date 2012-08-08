package com.jetbrains.python.run;

import com.intellij.openapi.module.Module;
import com.jetbrains.python.debugger.remote.PyPathMappingSettings;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author Leonid Shalupov
 */
public interface AbstractPythonRunConfigurationParams {
  String getInterpreterOptions();

  void setInterpreterOptions(String interpreterOptions);

  String getWorkingDirectory();

  void setWorkingDirectory(String workingDirectory);

  @Nullable
  String getSdkHome();

  void setSdkHome(String sdkHome);

  @Nullable
  Module getModule();

  void setModule(Module module);

  boolean isUseModuleSdk();

  void setUseModuleSdk(boolean useModuleSdk);

  boolean isPassParentEnvs();

  void setPassParentEnvs(boolean passParentEnvs);

  Map<String, String> getEnvs();

  void setEnvs(final Map<String, String> envs);

  @Nullable
  PyPathMappingSettings getMappingSettings();

  void setMappingSettings(@Nullable PyPathMappingSettings mappingSettings);
}
