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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.remote.PathMappingProvider;
import com.intellij.remote.RemoteMappingsManager;
import com.intellij.remote.RemoteSdkAdditionalData;
import com.intellij.remote.RemoteSdkProperties;
import com.intellij.util.PathMapper;
import com.intellij.util.PathMappingSettings;
import com.jetbrains.extensions.python.ProgressManagerExtKt;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.console.PyConsoleProcessHandler;
import com.jetbrains.python.console.PydevConsoleCommunication;
import com.jetbrains.python.console.PythonConsoleView;
import com.jetbrains.python.remote.PyRemotePathMapper.PyPathMappingType;
import kotlin.jvm.functions.Function0;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.Collection;

/**
 * @author traff
 */
public abstract class PythonRemoteInterpreterManager {
  public final static ExtensionPointName<PythonRemoteInterpreterManager> EP_NAME =
    ExtensionPointName.create("Pythonid.remoteInterpreterManager");

  public final static Key<PathMapper> PATH_MAPPING_SETTINGS_KEY = Key.create("PATH_MAPPING_SETTINGS_KEY");

  public final static Key<PathMappingSettings> ADDITIONAL_MAPPINGS = Key.create("ADDITIONAL_MAPPINGS");

  public static final String PYTHON_PREFIX = "python";

  public abstract boolean editSdk(@NotNull Project project, @NotNull SdkModificator sdkModificator, @NotNull Collection<Sdk> existingSdks);

  @Nullable
  public static PythonRemoteInterpreterManager getInstance() {
    if (EP_NAME.getExtensions().length > 0) {
      return EP_NAME.getExtensions()[0];
    }
    else {
      return null;
    }
  }

  public static void addUnbuffered(@NotNull ParamsGroup exeGroup) {
    for (String param : exeGroup.getParametersList().getParameters()) {
      if ("-u".equals(param)) {
        return;
      }
    }
    exeGroup.addParameter("-u");
  }

  public static String toSystemDependent(@NotNull String path, boolean isWin) {
    char separator = isWin ? '\\' : '/';
    return FileUtil.toSystemIndependentName(path).replace('/', separator);
  }

  public static void addHelpersMapping(@NotNull RemoteSdkProperties data, @NotNull PyRemotePathMapper pathMapper) {
    pathMapper.addMapping(PythonHelpersLocator.getHelpersRoot().getPath(), data.getHelpersPath(), PyPathMappingType.HELPERS);
  }

  @NotNull
  public static PyRemotePathMapper appendBasicMappings(@Nullable Project project,
                                                       @Nullable PyRemotePathMapper pathMapper,
                                                       @NotNull RemoteSdkAdditionalData<?> data) {
    @NotNull PyRemotePathMapper newPathMapper = PyRemotePathMapper.cloneMapper(pathMapper);

    addHelpersMapping(data, newPathMapper);

    newPathMapper.addAll(data.getPathMappings().getPathMappings(), PyRemotePathMapper.PyPathMappingType.SYS_PATH);

    if (project != null) {
      final RemoteMappingsManager.Mappings mappings =
        RemoteMappingsManager.getInstance(project).getForServer(PYTHON_PREFIX, data.getSdkId());
      if (mappings != null) {
        newPathMapper.addAll(mappings.getSettings(), PyRemotePathMapper.PyPathMappingType.USER_DEFINED);
      }

      for (PathMappingProvider mappingProvider : PathMappingProvider.getSuitableMappingProviders(data)) {
        PathMappingSettings settings =
          ProgressManagerExtKt.runUnderProgress(ProgressManager.getInstance(), "Accessing remote interpreter...",
                                                new Function0<PathMappingSettings>() {
            @Override
            public PathMappingSettings invoke() { //Path mapping may require external process with WSL
              return mappingProvider.getPathMappingSettings(project, data);
            }
          });
        newPathMapper.addAll(settings.getPathMappings(), PyRemotePathMapper.PyPathMappingType.REPLICATED_FOLDER);
      }
    }

    return newPathMapper;
  }

  @NotNull
  public abstract SdkAdditionalData loadRemoteSdkData(@NotNull Sdk sdk, @Nullable Element additional);

  @NotNull
  public abstract PyConsoleProcessHandler createConsoleProcessHandler(@NotNull Process process,
                                                                      @NotNull PythonConsoleView view,
                                                                      @NotNull PydevConsoleCommunication consoleCommunication,
                                                                      @NotNull String commandLine,
                                                                      @NotNull Charset charset,
                                                                      @Nullable PyRemotePathMapper pathMapper,
                                                                      @NotNull PyRemoteSocketToLocalHostProvider remoteSocketProvider);

  @NotNull
  public abstract String[] chooseRemoteFiles(@NotNull Project project, @NotNull PyRemoteSdkAdditionalDataBase data, boolean foldersOnly)
    throws ExecutionException, InterruptedException;

  public abstract void runVagrant(@NotNull String vagrantFolder, @Nullable String machineName) throws ExecutionException;

  /**
   * @author traff
   */
  public static class PyHelpersNotReadyException extends RuntimeException {
    public PyHelpersNotReadyException(Throwable cause) {
      super("Python helpers are not copied yet to the remote host. Please wait until remote interpreter initialization finishes.", cause);
    }
  }
}
