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
package com.jetbrains.python.remote;

import com.google.common.base.Function;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.remote.*;
import com.intellij.util.NullableConsumer;
import com.intellij.util.PathMapper;
import com.intellij.util.PathMappingSettings;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.console.PyConsoleProcessHandler;
import com.jetbrains.python.console.PydevConsoleCommunication;
import com.jetbrains.python.console.PythonConsoleView;
import com.jetbrains.python.remote.PyRemotePathMapper.PyPathMappingType;
import com.jetbrains.python.sdk.skeletons.PySkeletonGenerator;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author traff
 */
public abstract class PythonRemoteInterpreterManager {
  public final static ExtensionPointName<PythonRemoteInterpreterManager> EP_NAME =
    ExtensionPointName.create("Pythonid.remoteInterpreterManager");
  public static final String WEB_DEPLOYMENT_PLUGIN_IS_DISABLED =
    "Remote interpreter can't be executed. Please enable the Remote Hosts Access plugin."; //TODO: this message is incorrect

  public final static Key<PathMapper> PATH_MAPPING_SETTINGS_KEY = Key.create("PATH_MAPPING_SETTINGS_KEY");

  public final static Key<PathMappingSettings> ADDITIONAL_MAPPINGS = Key.create("ADDITIONAL_MAPPINGS");


  /**
   * @deprecated use {@link com.jetbrains.python.run.PyRemoteProcessStarterManager#startRemoteProcess(Project, GeneralCommandLine, PythonRemoteInterpreterManager, PyRemoteSdkAdditionalDataBase, PyRemotePathMapper)}
   */
  @Deprecated
  public abstract ProcessHandler startRemoteProcess(@Nullable Project project,
                                                    @NotNull PyRemoteSdkCredentials data,
                                                    @NotNull GeneralCommandLine commandLine,
                                                    @NotNull PyRemotePathMapper pathMapper)
    throws RemoteSdkException;

  public abstract void addRemoteSdk(Project project, Component parentComponent, Collection<Sdk> existingSdks,
                                    NullableConsumer<Sdk> sdkCallback);


  public abstract boolean editSdk(@NotNull Project project, @NotNull SdkModificator sdkModificator, Collection<Sdk> existingSdks);

  public abstract PySkeletonGenerator createRemoteSkeletonGenerator(@Nullable Project project,
                                                                    @Nullable Component ownerComponent,
                                                                    @NotNull Sdk sdk,
                                                                    String path) throws ExecutionException;

  public abstract boolean ensureCanWrite(@Nullable Object projectOrComponent, RemoteSdkCredentials data, String path);

  /**
   * @param sdk current sdk
   * @return project synchronizer for this sdk. See {@link PyProjectSynchronizer} for more info
   * @see PyProjectSynchronizer
   */
  @Nullable
  public abstract PyProjectSynchronizer getSynchronizer(@NotNull final Sdk sdk);

  public abstract void copyFromRemote(Sdk sdk, @NotNull Project project,
                                      RemoteSdkCredentials data,
                                      List<PathMappingSettings.PathMapping> mappings);

  /**
   * Creates form to browse remote box.
   * You need to show it to user using dialog.
   *
   * @return null if remote sdk can't be browsed.
   * First argument is consumer to get path, chosen by user.
   * Second is panel to display to user
   * @throws ExecutionException   credentials can't be obtained due to remote server error
   * @throws InterruptedException credentials can't be obtained due to remote server error
   */
  @Nullable
  public abstract Pair<Supplier<String>, JPanel> createServerBrowserForm(@NotNull final Sdk remoteSdk)
    throws ExecutionException, InterruptedException;

  /**
   * Short-cut to get {@link PyProjectSynchronizer} for sdk or null if sdk does not have any
   */
  @Nullable
  public static PyProjectSynchronizer getSynchronizerInstance(@NotNull final Sdk sdk) {
    final PythonRemoteInterpreterManager remoteManager = getInstance();
    if (remoteManager == null) {
      return null;
    }
    final PyProjectSynchronizer synchronizer = remoteManager.getSynchronizer(sdk);
    if (synchronizer == null) {
      return null;
    }
    return synchronizer;
  }


  @Nullable
  public static PythonRemoteInterpreterManager getInstance() {
    if (EP_NAME.getExtensions().length > 0) {
      return EP_NAME.getExtensions()[0];
    }
    else {
      return null;
    }
  }

  public static void addUnbuffered(ParamsGroup exeGroup) {
    for (String param : exeGroup.getParametersList().getParameters()) {
      if ("-u".equals(param)) {
        return;
      }
    }
    exeGroup.addParameter("-u");
  }

  public static String toSystemDependent(String path, boolean isWin) {
    char separator = isWin ? '\\' : '/';
    return FileUtil.toSystemIndependentName(path).replace('/', separator);
  }

  public static void addHelpersMapping(@NotNull RemoteSdkProperties data, @NotNull PyRemotePathMapper pathMapper) {
    pathMapper.addMapping(PythonHelpersLocator.getHelpersRoot().getPath(), data.getHelpersPath(), PyPathMappingType.HELPERS);
  }

  @NotNull
  public abstract PyRemotePathMapper setupMappings(@Nullable Project project,
                                                   @NotNull PyRemoteSdkAdditionalDataBase data,
                                                   @Nullable PyRemotePathMapper pathMapper);

  public abstract SdkAdditionalData loadRemoteSdkData(Sdk sdk, Element additional);

  public abstract PyConsoleProcessHandler createConsoleProcessHandler(RemoteProcess process,
                                                                      PythonConsoleView view,
                                                                      PydevConsoleCommunication consoleCommunication,
                                                                      String commandLine,
                                                                      Charset charset,
                                                                      PyRemotePathMapper pathMapper,
                                                                      PyRemoteSocketToLocalHostProvider remoteSocketProvider);

  @NotNull
  public abstract RemoteSdkCredentialsProducer<PyRemoteSdkCredentials> getRemoteSdkCredentialsProducer(Function<RemoteCredentials, PyRemoteSdkCredentials> credentialsTransformer,
                                                                                                       RemoteConnectionCredentialsWrapper connectionWrapper);

  public abstract String getInterpreterVersion(@Nullable Project project, PyRemoteSdkAdditionalDataBase data) throws RemoteSdkException;

  public abstract String[] chooseRemoteFiles(Project project, @NotNull PyRemoteSdkAdditionalDataBase data, boolean foldersOnly)
    throws ExecutionException, InterruptedException;

  public static class PyRemoteInterpreterExecutionException extends ExecutionException {

    public PyRemoteInterpreterExecutionException() {
      super(WEB_DEPLOYMENT_PLUGIN_IS_DISABLED);
    }
  }

  public static class PyRemoteInterpreterRuntimeException extends RuntimeException {

    public PyRemoteInterpreterRuntimeException() {
      super(WEB_DEPLOYMENT_PLUGIN_IS_DISABLED);
    }
  }

  public abstract void runVagrant(@NotNull String vagrantFolder, @Nullable String machineName) throws ExecutionException;
}
