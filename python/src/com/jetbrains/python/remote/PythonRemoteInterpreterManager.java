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
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.remote.*;
import com.intellij.util.NullableConsumer;
import com.intellij.util.PathMappingSettings;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.console.PyConsoleProcessHandler;
import com.jetbrains.python.console.PydevConsoleCommunication;
import com.jetbrains.python.console.PythonConsoleView;
import com.jetbrains.python.sdk.skeletons.PySkeletonGenerator;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;

/**
 * @author traff
 */
public abstract class PythonRemoteInterpreterManager {
  public final static ExtensionPointName<PythonRemoteInterpreterManager> EP_NAME =
    ExtensionPointName.create("Pythonid.remoteInterpreterManager");
  public static final String WEB_DEPLOYMENT_PLUGIN_IS_DISABLED =
    "Remote interpreter can't be executed. Please enable the Remote Hosts Access plugin."; //TODO: this message is incorrect

  public final static Key<PathMappingSettings> PATH_MAPPING_SETTINGS_KEY = Key.create("PATH_MAPPING_SETTINGS_KEY");

  public abstract ProcessHandler startRemoteProcess(@Nullable Project project,
                                                    @NotNull PyRemoteSdkCredentials data,
                                                    @NotNull GeneralCommandLine commandLine,
                                                    @NotNull PathMappingSettings mappingSettings)
    throws RemoteSdkException;

  public abstract ProcessHandler startRemoteProcessWithPid(@Nullable Project project,
                                                           @NotNull PyRemoteSdkCredentials data,
                                                           @NotNull GeneralCommandLine commandLine,
                                                           @NotNull
                                                           PathMappingSettings mappingSettings)
    throws RemoteSdkException;

  public abstract void addRemoteSdk(Project project, Component parentComponent, Collection<Sdk> existingSdks,
                                    NullableConsumer<Sdk> sdkCallback);


  public abstract ProcessOutput runRemoteProcess(@Nullable Project project,
                                                 RemoteSdkCredentials data,
                                                 @NotNull PathMappingSettings mappings,
                                                 String[] command,
                                                 @Nullable String workingDir,
                                                 boolean askForSudo)
    throws RemoteSdkException;

  @NotNull
  public abstract RemoteSshProcess createRemoteProcess(@Nullable Project project,
                                                       @NotNull PyRemoteSdkCredentials data,
                                                       @NotNull PathMappingSettings mappings,
                                                       @NotNull GeneralCommandLine commandLine, boolean allocatePty)
    throws RemoteSdkException;

  public abstract boolean editSdk(@NotNull Project project, @NotNull SdkModificator sdkModificator, Collection<Sdk> existingSdks);

  public abstract PySkeletonGenerator createRemoteSkeletonGenerator(@Nullable Project project,
                                                                    @Nullable Component ownerComponent,
                                                                    @NotNull Sdk sdk,
                                                                    String path) throws ExecutionException;

  public abstract boolean ensureCanWrite(@Nullable Object projectOrComponent, RemoteSdkCredentials data, String path);

  @Nullable
  public abstract RemoteProjectSettings showRemoteProjectSettingsDialog(VirtualFile baseDir, RemoteSdkCredentials data);

  public abstract void createDeployment(Project project,
                                        VirtualFile projectDir,
                                        RemoteProjectSettings settings,
                                        RemoteSdkCredentials data);

  public abstract void copyFromRemote(Sdk sdk, @NotNull Project project,
                                      RemoteSdkCredentials data,
                                      List<PathMappingSettings.PathMapping> mappings);

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

  public static void addHelpersMapping(@NotNull RemoteSdkProperties data, @Nullable PathMappingSettings newMappingSettings) {
    if (newMappingSettings == null) {
      newMappingSettings = new PathMappingSettings();
    }
    newMappingSettings.addMapping(PythonHelpersLocator.getHelpersRoot().getPath(), data.getHelpersPath());
  }

  @NotNull
  public abstract PathMappingSettings setupMappings(@Nullable Project project,
                                                    @NotNull PyRemoteSdkAdditionalDataBase data,
                                                    @Nullable PathMappingSettings mappingSettings);

  public abstract SdkAdditionalData loadRemoteSdkData(Sdk sdk, Element additional);

  public abstract PyConsoleProcessHandler createConsoleProcessHandler(Process process,
                                                                      PyRemoteSdkCredentials data,
                                                                      PythonConsoleView view,
                                                                      PydevConsoleCommunication consoleCommunication,
                                                                      String commandLine, Charset charset, PathMappingSettings settings);

  @NotNull
  public abstract RemoteSdkCredentialsProducer<PyRemoteSdkCredentials> getRemoteSdkCredentialsProducer(Function<RemoteCredentials, PyRemoteSdkCredentials> credentialsTransformer,
                                                                                              RemoteConnectionCredentialsWrapper connectionWrapper);

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

