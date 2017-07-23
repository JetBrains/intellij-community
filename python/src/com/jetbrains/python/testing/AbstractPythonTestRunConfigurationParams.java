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
package com.jetbrains.python.testing;

import com.jetbrains.python.run.AbstractPythonRunConfigurationParams;
import org.jetbrains.annotations.NotNull;

public interface AbstractPythonTestRunConfigurationParams {
  AbstractPythonRunConfigurationParams getBaseParams();

  String getClassName();
  void setClassName(String className);

  String getFolderName();
  void setFolderName(String folderName);

  String getScriptName();
  void setScriptName(@NotNull String scriptName);

  String getMethodName();
  void setMethodName(String methodName);

  AbstractPythonLegacyTestRunConfiguration.TestType getTestType();
  void setTestType(AbstractPythonLegacyTestRunConfiguration.TestType testType);

  boolean usePattern();
  void usePattern(boolean isPureUnittest);

  String getPattern();
  void setPattern(String pattern);

  boolean shouldAddContentRoots();
  boolean shouldAddSourceRoots();
  void setAddContentRoots(boolean addContentRoots);
  void setAddSourceRoots(boolean addSourceRoots);
}
