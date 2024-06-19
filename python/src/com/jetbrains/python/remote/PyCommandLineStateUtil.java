// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.remote;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.remote.RemoteFile;
import com.intellij.remote.RemoteProcessUtil;
import com.intellij.remote.RemoteSdkProperties;
import com.intellij.util.PathMapper;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.sdk.PythonEnvUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@SuppressWarnings("SameParameterValue")
public final class PyCommandLineStateUtil {
  // the environment variable used by BDD to hold a set of folder or feature files
  private static final String PY_STUFF_TO_RUN = "PY_STUFF_TO_RUN";

  private PyCommandLineStateUtil() { }

  public static void remap(@NotNull RemoteSdkProperties sdkProperties, @NotNull GeneralCommandLine commandLine, @NotNull PathMapper pathMapper) {
    remap(sdkProperties.getInterpreterPath(), commandLine, pathMapper);
  }

  public static void remap(@NotNull String interpreterPath, @NotNull GeneralCommandLine commandLine, @NotNull PathMapper pathMapper) {
    ParamsGroup paramsGroup = commandLine.getParametersList().getParamsGroup(PythonCommandLineState.GROUP_SCRIPT);
    remapParameters(interpreterPath, pathMapper, paramsGroup, commandLine.getWorkDirectory());

    remapEnvPaths(commandLine.getEnvironment(), pathMapper, interpreterPath, PythonEnvUtil.PYTHONPATH);
    remapEnvPaths(commandLine.getEnvironment(), pathMapper, interpreterPath, PyDebugRunner.IDE_PROJECT_ROOTS);
    remapEnvStuffPaths(commandLine.getEnvironment(), pathMapper, interpreterPath, PY_STUFF_TO_RUN);
  }

  public static void remapParameters(
    @NotNull String interpreterPath,
    @NotNull PathMapper pathMapper,
    @Nullable ParamsGroup paramsGroup,
    @Nullable File workDirectory
  ) {
    if (paramsGroup != null) {
      if (!paramsGroup.getParameters().isEmpty()) {
        makeParamAbsoluteIfRelative(paramsGroup, 0, workDirectory);
      }

      int i = 0;
      boolean isWin = RemoteFile.isWindowsPath(interpreterPath);
      for (String param : paramsGroup.getParameters()) {
        if (pathMapper.canReplaceLocal(param)) {
          paramsGroup.getParametersList().set(i++, RemoteFile.createRemoteFile(pathMapper.convertToRemote(param), isWin).getPath());
        }
      }
    }
  }

  private static void makeParamAbsoluteIfRelative(ParamsGroup paramsGroup, int paramIndex, @Nullable File workDirectory) {
    String param = paramsGroup.getParameters().get(paramIndex);
    if (!new File(param).isAbsolute() && workDirectory != null) {
      File paramFile = new File(workDirectory, param);
      if (paramFile.exists()) {
        paramsGroup.getParametersList().set(paramIndex, paramFile.getPath());
      }
    }
  }

  private static void remapEnvPaths(Map<String, String> env, PathMapper pathMapper, String interpreterPath, String envKey) {
    if (env.isEmpty()) return;

    String envPaths = env.get(envKey);
    if (envPaths != null) {
      env.put(envKey, RemoteProcessUtil.remapPathsList(envPaths, pathMapper, interpreterPath));
    }
  }

  private static void remapEnvStuffPaths(Map<String, String> env, PathMapper pathMapper, String interpreterPath, String envKey) {
    String envPaths = env.get(envKey);
    if (envPaths != null) {
      env.put(envKey, remapStuffPathsList(envPaths, pathMapper, interpreterPath));
    }
  }

  public static @NotNull String remapStuffPathsList(@NotNull String pathsValue, @NotNull PathMapper pathMapper, @NotNull String interpreterPath) {
    boolean isWin = RemoteFile.isWindowsPath(interpreterPath);
    List<String> mappedPaths = new ArrayList<>();
    for (String path : pathsValue.split(Pattern.quote("|"))) {
      mappedPaths.add(RemoteFile.createRemoteFile(pathMapper.convertToRemote(path), isWin).getPath());
    }
    return String.join("|", mappedPaths);
  }
}
