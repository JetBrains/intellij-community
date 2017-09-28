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
package com.jetbrains.python.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.remote.PyRemotePathMapper;
import com.jetbrains.python.remote.PyRemoteProcessHandlerBase;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author Alexander Koshevoy
 */
public interface PyRemoteProcessStarterManager {
  ExtensionPointName<PyRemoteProcessStarterManager> EP_NAME = ExtensionPointName.create("Pythonid.remoteProcessStarterManager");

  boolean supports(@NotNull PyRemoteSdkAdditionalDataBase sdkAdditionalData);

  @NotNull
  PyRemoteProcessHandlerBase startRemoteProcess(@Nullable Project project,
                                                @NotNull GeneralCommandLine commandLine,
                                                @NotNull PythonRemoteInterpreterManager manager,
                                                @NotNull PyRemoteSdkAdditionalDataBase sdkAdditionalData,
                                                @NotNull PyRemotePathMapper pathMapper) throws ExecutionException, InterruptedException;

  @NotNull
  ProcessOutput executeRemoteProcess(@Nullable Project project,
                                     @NotNull String[] command,
                                     @Nullable String workingDir,
                                     @NotNull PythonRemoteInterpreterManager manager,
                                     @NotNull PyRemoteSdkAdditionalDataBase sdkAdditionalData,
                                     @NotNull PyRemotePathMapper pathMapper, boolean askForSudo, boolean checkHelpers) throws ExecutionException, InterruptedException;

  default ProcessOutput executeRemoteProcess(@Nullable Project project,
                                            @NotNull String[] command,
                                            @Nullable String workingDir,
                                            @NotNull PythonRemoteInterpreterManager manager,
                                            @NotNull PyRemoteSdkAdditionalDataBase sdkAdditionalData,
                                            @NotNull PyRemotePathMapper pathMapper,
                                            boolean askForSudo,
                                            @NotNull Set<String> checkHelpersPaths) throws ExecutionException, InterruptedException {
    return executeRemoteProcess(project, command, workingDir, manager, sdkAdditionalData, pathMapper, askForSudo, !checkHelpersPaths.isEmpty());
  }

  String getFullInterpreterPath(@NotNull PyRemoteSdkAdditionalDataBase sdkAdditionalData)
    throws ExecutionException, InterruptedException;
}
