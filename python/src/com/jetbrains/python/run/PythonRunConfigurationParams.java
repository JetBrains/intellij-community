// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run;

import org.jetbrains.annotations.NotNull;

/**
 * @author Leonid Shalupov
 */
public interface PythonRunConfigurationParams {
  AbstractPythonRunConfigurationParams getBaseParams();

  String getScriptName();

  void setScriptName(String scriptName);

  String getScriptParameters();

  void setScriptParameters(String scriptParameters);

  boolean showCommandLineAfterwards();
  void setShowCommandLineAfterwards(boolean showCommandLineAfterwards);

  boolean emulateTerminal();
  void setEmulateTerminal(boolean emulateTerminal);

  default boolean isModuleMode() {
    return false;
  }

  default void setModuleMode(boolean moduleMode) {
  }

  default boolean isRedirectInput() {
    return false;
  }

  default void setRedirectInput(boolean isRedirectInput) {

  }

  default @NotNull String getInputFile() {
    return "";
  }

  default void setInputFile(@NotNull String inputFile) {

  }
}

