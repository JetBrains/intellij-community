// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.util.PathMappingSettings;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public interface PythonRunParams {
  String getInterpreterOptions();

  void setInterpreterOptions(String interpreterOptions);

  String getWorkingDirectory();

  void setWorkingDirectory(String workingDirectory);

  /**
   * @deprecated Use {@link #getSdk()} instead.
   * The {@link #getSdkHome()} can be not unique for different interpreters if absolute path to python is the same.
   */
  @Deprecated
  @Nullable
  String getSdkHome();

  /**
   * @deprecated Use {@link #setSdk} instead.
   * The {@link #setSdkHome} can be not unique for different interpreters if absolute path to python is the same.
   */
  @Deprecated
  void setSdkHome(String sdkHome);

  @Nullable
  default Sdk getSdk() {
    return null;
  }

  default void setSdk(@Nullable Sdk sdk) {

  }

  void setModule(Module module);

  String getModuleName();

  boolean isUseModuleSdk();

  void setUseModuleSdk(boolean useModuleSdk);

  boolean isPassParentEnvs();

  void setPassParentEnvs(boolean passParentEnvs);

  Map<String, String> getEnvs();

  void setEnvs(Map<String, String> envs);

  @Nullable
  PathMappingSettings getMappingSettings();

  void setMappingSettings(@Nullable PathMappingSettings mappingSettings);

  boolean shouldAddContentRoots();

  boolean shouldAddSourceRoots();

  void setAddContentRoots(boolean flag);

  void setAddSourceRoots(boolean flag);
}
