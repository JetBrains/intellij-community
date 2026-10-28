// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run;

import com.intellij.execution.EnvFilesOptions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.python.sdk.backend.PythonInterpreter;
import com.intellij.python.sdk.backend.PythonInterpreterExtKt;
import com.intellij.python.sdk.backend.PythonInterpreterRegistryKt;
import com.intellij.util.PathMappingSettings;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public interface PythonRunParams extends EnvFilesOptions {
  String getInterpreterOptions();

  void setInterpreterOptions(String interpreterOptions);

  String getWorkingDirectory();

  void setWorkingDirectory(String workingDirectory);

  /**
   * The interpreter that these parameters name themselves, or {@code null}. When {@link #isUseModuleSdk()} is set, the
   * interpreter of the module is used instead.
   */
  @ApiStatus.Internal
  default @Nullable PythonInterpreter getPythonInterpreter() {
    return null;
  }

  /** Makes {@code interpreter} the interpreter that these parameters name themselves, see {@link #getPythonInterpreter()}. */
  @ApiStatus.Internal
  default void setPythonInterpreter(@Nullable PythonInterpreter interpreter) {
  }

  /**
   * The SDK of {@link #getPythonInterpreter()}.
   *
   * @deprecated Use {@link #getPythonInterpreter()}. A caller that still needs the SDK gets it from the interpreter.
   */
  @Deprecated
  default @Nullable Sdk getSdk() {
    final PythonInterpreter interpreter = getPythonInterpreter();
    return interpreter != null ? PythonInterpreterExtKt.getSdkAPI(interpreter) : null;
  }

  /**
   * Makes the interpreter of {@code sdk} the interpreter of these parameters, see {@link #setPythonInterpreter}.
   *
   * @deprecated Use {@link #setPythonInterpreter}.
   */
  @Deprecated
  default void setSdk(@Nullable Sdk sdk) {
    setPythonInterpreter(sdk != null ? PythonInterpreterRegistryKt.findPythonInterpreterInOpenProjectsIfReady(sdk) : null);
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

  @ApiStatus.Internal
  void setDebugJustMyCode(boolean flag);

  @ApiStatus.Internal
  boolean shouldDebugJustMyCode();
}
