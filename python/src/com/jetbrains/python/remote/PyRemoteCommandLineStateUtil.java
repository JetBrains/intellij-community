// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.remote;

import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.openapi.util.Pair;
import com.intellij.remote.RemoteFile;
import com.intellij.remote.RemoteSdkException;
import com.intellij.util.PathMapper;
import com.jetbrains.python.debugger.PyDebugRunner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Use {@link PyRemoteCommandLinePatcherKt} instead
 */
final class PyRemoteCommandLineStateUtil {
  private PyRemoteCommandLineStateUtil() {
  }

  static void patchDebugParams(@NotNull String helpersPath,
                               @NotNull PyRemoteSocketToLocalHostProvider remoteSocketProvider,
                               @NotNull ParamsGroup debugParams)
    throws RemoteSdkException {
    debugParams.getParametersList().set(0, new RemoteFile(helpersPath, PyDebugRunner.DEBUGGER_MAIN).getPath());

    Pair<String, Integer> socket = remoteSocketProvider.getRemoteSocket(Integer.parseInt(debugParams.getParametersList()
                                                                                           .get(PyDebugRunner.findIndex(
                                                                                             debugParams.getParameters(),
                                                                                             PyDebugRunner.PORT_PARAM))));

    int clientParamIndex = PyDebugRunner.findIndex(debugParams.getParameters(), PyDebugRunner.CLIENT_PARAM);
    if (clientParamIndex != -1) {
      debugParams.getParametersList().set(clientParamIndex, socket.getFirst());
    }

    debugParams.getParametersList()
      .set(PyDebugRunner.findIndex(debugParams.getParameters(), PyDebugRunner.PORT_PARAM), Integer.toString(socket.getSecond()));
  }

  static void patchProfileParams(@NotNull String interpreterPath,
                                 @NotNull PyRemoteSocketToLocalHostProvider remoteSocketProvider,
                                 @NotNull ParamsGroup profileParams,
                                 @Nullable File workDirectory,
                                 @NotNull PathMapper pathMapper)
    throws RemoteSdkException {
    PyCommandLineStateUtil.remapParameters(interpreterPath, pathMapper, profileParams, workDirectory);

    Pair<String, Integer> socket = remoteSocketProvider.getRemoteSocket(Integer.parseInt(profileParams.getParametersList()
                                                                                           .get(2)));

    profileParams.getParametersList()
      .set(1, socket.getFirst());


    profileParams.getParametersList()
      .set(2, Integer.toString(socket.getSecond()));
  }

  static void patchCoverageParams(@NotNull String interpreterPath,
                                  @NotNull ParamsGroup coverageParams,
                                  @Nullable File workDirectory,
                                  @NotNull PathMapper pathMapper)
    throws RemoteSdkException {

    PyCommandLineStateUtil.remapParameters(interpreterPath, pathMapper, coverageParams, workDirectory);

    int i = 0;
    for (String param : coverageParams.getParameters()) {
      String omitPrefix = "--omit=";
      if (param.startsWith(omitPrefix)) {
        String path = param.substring(omitPrefix.length());
        coverageParams.getParametersList().set(i, omitPrefix +
                                                  RemoteFile.detectSystemByPath(interpreterPath).
                                                    createRemoteFile(pathMapper.convertToRemote(path)).getPath());
      }
      i++;
    }
  }
}
