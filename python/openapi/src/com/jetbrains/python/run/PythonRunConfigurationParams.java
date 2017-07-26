/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  boolean showCommandLineAfterwards();
  void setShowCommandLineAfterwards(boolean showCommandLineAfterwards);

  boolean emulateTerminal();
  void setEmulateTerminal(boolean emulateTerminal);

  boolean mixedDebugMode();
  void setMixedDebugMode(boolean mixedDebugMode);

  String getDebuggableExternalLibs();
  void setDebuggableExternalLibs(String debuggableExternalLibs);
}

