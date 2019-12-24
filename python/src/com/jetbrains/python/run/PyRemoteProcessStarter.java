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

import com.google.common.net.HostAndPort;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.util.Key;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.remote.PyRemotePathMapper;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.remote.UnsupportedPythonSdkTypeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public class PyRemoteProcessStarter {
  public static final Key<Boolean> OPEN_FOR_INCOMING_CONNECTION = Key.create("OPEN_FOR_INCOMING_CONNECTION");
  public static final Key<HostAndPort> WEB_SERVER_HOST_AND_PORT = new Key<>("WEB_SERVER_HOST_AND_PORT");

  @NotNull
  public ProcessHandler startRemoteProcess(@NotNull Sdk sdk,
                                           @NotNull GeneralCommandLine commandLine,
                                           @Nullable Project project,
                                           @Nullable PyRemotePathMapper pathMapper)
    throws ExecutionException {
    ProcessHandler processHandler;

    try {
      processHandler = doStartRemoteProcess(sdk, commandLine, project, pathMapper);
    }
    catch (UnsupportedPythonSdkTypeException e) {
      throw new ExecutionException(PyBundle.message("remote.interpreter.support.is.not.available", sdk.getName()), e);
    }
    catch (ExecutionException e) {
      final Application application = ApplicationManager.getApplication();
      if (application != null && (application.isUnitTestMode() || application.isHeadlessEnvironment())) {
        throw new RuntimeException(e);
      }
      throw new ExecutionException("Can't run remote python interpreter: " + e.getMessage(), e);
    }
    ProcessTerminatedListener.attach(processHandler);
    return processHandler;
  }

  /**
   * Starts a process using corresponding support (e.g. SSH, Vagrant, Docker,
   * etc.) for the provided {@code sdk}.
   *
   * @param sdk         the Python SDK
   * @param commandLine the command line to start the Python interpreter
   * @param project     the optional project for additional path mappings
   * @param pathMapper  the mapping between paths on the host machine and the one
   *                    the process will be executed on
   * @return process handler for created process
   * @throws ExecutionException
   * @throws UnsupportedPythonSdkTypeException if support cannot be found for
   *                                           the type of the provided sdk
   */
  @NotNull
  protected ProcessHandler doStartRemoteProcess(@NotNull Sdk sdk,
                                                @NotNull final GeneralCommandLine commandLine,
                                                @Nullable final Project project,
                                                @Nullable PyRemotePathMapper pathMapper)
    throws ExecutionException {
    SdkAdditionalData data = sdk.getSdkAdditionalData();
    assert data instanceof PyRemoteSdkAdditionalDataBase;
    final PyRemoteSdkAdditionalDataBase pyRemoteSdkAdditionalDataBase = (PyRemoteSdkAdditionalDataBase)data;

    final PyRemotePathMapper extendedPathMapper =
      PythonRemoteInterpreterManager.appendBasicMappings(project, pathMapper, pyRemoteSdkAdditionalDataBase);

    try {
      return PyRemoteProcessStarterManagerUtil
        .getManager(pyRemoteSdkAdditionalDataBase)
        .startRemoteProcess(project, commandLine, pyRemoteSdkAdditionalDataBase, extendedPathMapper);
    }
    catch (InterruptedException e) {
      throw new ExecutionException(e);
    }
  }
}
