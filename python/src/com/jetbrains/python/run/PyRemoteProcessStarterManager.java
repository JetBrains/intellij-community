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
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.remote.PyRemotePathMapper;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * For anything but plain code execution consider introducing a separate
 * extension point with implementations for different
 * {@link com.intellij.remote.CredentialsType} using
 * {@link PyRemoteSdkAdditionalDataBase#switchOnConnectionType(com.intellij.remote.ext.CredentialsCase[])}.
 *
 * @see PyRemoteProcessStarterManagerUtil
 */
public interface PyRemoteProcessStarterManager {
  ExtensionPointName<PyRemoteProcessStarterManager> EP_NAME = ExtensionPointName.create("Pythonid.remoteProcessStarterManager");

  boolean supports(@NotNull PyRemoteSdkAdditionalDataBase sdkAdditionalData);

  @NotNull
  ProcessHandler startRemoteProcess(@Nullable Project project,
                                    @NotNull GeneralCommandLine commandLine,
                                    @NotNull PyRemoteSdkAdditionalDataBase sdkAdditionalData,
                                    @NotNull PyRemotePathMapper pathMapper) throws ExecutionException, InterruptedException;

  @NotNull
  ProcessOutput executeRemoteProcess(@Nullable Project project,
                                     String @NotNull [] command,
                                     @Nullable String workingDir,
                                     @NotNull PyRemoteSdkAdditionalDataBase sdkAdditionalData,
                                     @NotNull PyRemotePathMapper pathMapper) throws ExecutionException, InterruptedException;
}
