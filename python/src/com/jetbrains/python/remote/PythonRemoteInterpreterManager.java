// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.remote;

import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.python.community.helpersLocator.PythonHelpersLocator;
import com.intellij.remote.PathMappingProvider;
import com.intellij.remote.RemoteMappingsManager;
import com.intellij.remote.RemoteSdkAdditionalData;
import com.intellij.remote.RemoteSdkProperties;
import com.intellij.util.PathMappingSettings;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.extensions.ProgressManagerExtKt;
import com.jetbrains.python.remote.PyRemotePathMapper.PyPathMappingType;
import kotlin.jvm.functions.Function0;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PythonRemoteInterpreterManager {
  public static final ExtensionPointName<PythonRemoteInterpreterManager> EP_NAME =
    ExtensionPointName.create("Pythonid.remoteInterpreterManager");

  public static final Key<PathMappingSettings> ADDITIONAL_MAPPINGS = Key.create("ADDITIONAL_MAPPINGS");

  public static final String PYTHON_PREFIX = "python";

  public static @Nullable PythonRemoteInterpreterManager getInstance() {
    return ContainerUtil.getFirstItem(EP_NAME.getExtensionList());
  }

  public static void addUnbuffered(@NotNull ParamsGroup exeGroup) {
    for (String param : exeGroup.getParametersList().getParameters()) {
      if ("-u".equals(param)) {
        return;
      }
    }
    exeGroup.addParameter("-u");
  }

  /**
   * @deprecated  use {@link java.nio.file.Path}
   */
  @ApiStatus.Internal
  @Deprecated
  public static String toSystemDependent(@NotNull String path, boolean isWin) {
    char separator = isWin ? '\\' : '/';
    return FileUtil.toSystemIndependentName(path).replace('/', separator);
  }

  public static void addHelpersMapping(@NotNull RemoteSdkProperties data, @NotNull PyRemotePathMapper pathMapper) {
    pathMapper.addMapping(PythonHelpersLocator.getCommunityHelpersRoot().toString(), data.getHelpersPath(), PyPathMappingType.HELPERS);
  }

  public static @NotNull PyRemotePathMapper appendBasicMappings(@Nullable Project project,
                                                                @Nullable PyRemotePathMapper pathMapper,
                                                                @NotNull RemoteSdkAdditionalData data) {
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
          ProgressManagerExtKt.runUnderProgress(ProgressManager.getInstance(),
                                                PyBundle.message("remote.interpreter.accessing.remote.interpreter.progress.title"),
                                                new Function0<>() {
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

  public static class PyHelpersNotReadyException extends RuntimeException {
    public PyHelpersNotReadyException(Throwable cause) {
      super("Python helpers are not copied yet to the remote host. Please wait until remote interpreter initialization finishes.", cause);
    }
  }
}
