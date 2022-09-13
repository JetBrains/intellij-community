// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run;

import com.google.common.net.HostAndPort;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.remote.PyRemotePathMapper;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalData;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.remote.UnsupportedPythonSdkTypeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated use targets API instead
 */
@Deprecated
public final class PyRemoteProcessStarter {
  public static final Key<Boolean> OPEN_FOR_INCOMING_CONNECTION = Key.create("OPEN_FOR_INCOMING_CONNECTION");
  public static final Key<HostAndPort> WEB_SERVER_HOST_AND_PORT = new Key<>("WEB_SERVER_HOST_AND_PORT");
  /**
   * This key is used to give the hint for the process starter that the
   * process is auxiliary.
   * <p>
   * As for now this flag takes effect for Docker Compose process starters
   * which uses {@code docker-compose run} command to the contrary of the usual
   * process execution using {@code docker-compose up} command.
   */
  public static final Key<Boolean> RUN_AS_AUXILIARY_PROCESS = Key.create("RUN_AS_AUXILIARY_PROCESS");

  private PyRemoteProcessStarter() {

  }
  @NotNull
  public static ProcessHandler startLegacyRemoteProcess(@NotNull PyRemoteSdkAdditionalData legacyAdditionalData,
                                                        @NotNull GeneralCommandLine commandLine,
                                                        @Nullable Project project,
                                                        @Nullable PyRemotePathMapper pathMapper)
    throws ExecutionException {
    ProcessHandler processHandler;

    try {
      processHandler = doStartLegacyRemoteProcess(legacyAdditionalData, commandLine, project, pathMapper);
    }
    catch (UnsupportedPythonSdkTypeException e) {
      throw new ExecutionException(PyBundle.message("remote.interpreter.support.is.not.available", legacyAdditionalData.getClass()), e);
    }
    catch (ExecutionException e) {
      final Application application = ApplicationManager.getApplication();
      if (application != null && (application.isUnitTestMode() || application.isHeadlessEnvironment())) {
        throw new RuntimeException(e);
      }
      throw new ExecutionException(PyBundle.message("python.remote.process.starter.can.t.run.remote.interpreter", e.getMessage()), e);
    }
    ProcessTerminatedListener.attach(processHandler);
    return processHandler;
  }

  /**
   * Starts a process using corresponding support (e.g. SSH, Vagrant, Docker,
   * etc.) for the provided {@code sdk}.
   *
   * @param legacyAdditionalData //TODO: DOC
   * @param commandLine          the command line to start the Python interpreter
   * @param project              the optional project for additional path mappings
   * @param pathMapper           the mapping between paths on the host machine and the one
   *                             the process will be executed on
   * @return process handler for created process
   * @throws UnsupportedPythonSdkTypeException if support cannot be found for
   *                                           the type of the provided sdk
   */
  @NotNull
  private static ProcessHandler doStartLegacyRemoteProcess(@NotNull PyRemoteSdkAdditionalData legacyAdditionalData,
                                                           @NotNull final GeneralCommandLine commandLine,
                                                           @Nullable final Project project,
                                                           @Nullable PyRemotePathMapper pathMapper)
    throws ExecutionException {
    final PyRemotePathMapper extendedPathMapper =
      PythonRemoteInterpreterManager.appendBasicMappings(project, pathMapper, legacyAdditionalData);

    try {
      return PyRemoteProcessStarterManagerUtil.getManager(legacyAdditionalData)
        .startRemoteProcess(project, commandLine, legacyAdditionalData, extendedPathMapper);
    }
    catch (InterruptedException e) {
      throw new ExecutionException(e);
    }
  }
}
