/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * @author traff
 */
public interface HelperPackage {
  void addToPythonPath(@NotNull Map<String, String> environment);

  /**
   * @return entry (directory or ZIP archive) that will be added to <tt>PYTHONPATH</tt> environment variable before the process is started.
   */
  @NotNull
  String getPythonPathEntry();

  void addToGroup(@NotNull ParamsGroup group, @NotNull GeneralCommandLine cmd);

  /**
   * @return the first parameter passed to Python interpreter that indicates which script to run. For scripts started as modules it's 
   * module name with <tt>-m</tt> flag, like <tt>-mpackage.module.name</tt>, and for average helpers it's full path to the script.
   */
  @NotNull
  String asParamString();

  @NotNull
  GeneralCommandLine newCommandLine(@NotNull String sdkPath, @NotNull List<String> parameters);
  
  /**
   * Version-sensitive version of {@link #newCommandLine(String, List)}. It adds additional directories with libraries inside python-helpers
   * depending on the version of pythonSdk: either {@link PythonHelper#PY2_HELPER_DEPENDENCIES_DIR} or 
   * {@link PythonHelper#PY3_HELPER_DEPENDENCIES_DIR}.
   *
   * @param pythonSdk   Python SDK containing interpreter that will be used to run this helper    
   * @param parameters  additional command line parameters of this helper
   * @return instance {@link GeneralCommandLine} used to start the process
   */
  @NotNull
  GeneralCommandLine newCommandLine(@NotNull Sdk pythonSdk, @NotNull List<String> parameters);
}
