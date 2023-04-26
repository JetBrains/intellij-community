// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.remote;

import com.google.common.net.HostAndPort;
import com.intellij.execution.KillableProcess;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.remote.ColoredRemoteProcessHandler;
import com.intellij.remote.RemoteProcess;
import com.intellij.remote.RemoteSdkCredentials;
import com.intellij.remote.RemoteSdkException;
import com.intellij.util.PathMapper;
import com.intellij.util.PathMappingSettings;
import com.jetbrains.python.debugger.PyDebugProcess;
import com.jetbrains.python.debugger.PyPositionConverter;
import com.jetbrains.python.debugger.remote.vfs.PyRemotePositionConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public final class PyRemoteProcessHandler extends ColoredRemoteProcessHandler<RemoteProcess>
  implements RemoteDebuggableProcessHandler, KillableProcess {
  private static final Logger LOG = Logger.getInstance(PyRemoteProcessHandler.class);

  public static final String LOG_PY_CHARM_FILE_MAPPING = "LOG: PyCharm: File mapping:";
  @NotNull private final PyRemotePathMapper myPathMapper;
  private final List<PathMappingSettings.PathMapping> myFileMappings = new ArrayList<>();
  @NotNull private final PyRemoteSocketToLocalHostProvider myRemoteSocketProvider;

  private PyRemoteProcessHandler(@NotNull RemoteProcess process,
                                 @NotNull String commandLine,
                                 @NotNull Charset charset,
                                 @Nullable PyRemotePathMapper pathMapper,
                                 @NotNull PyRemoteSocketToLocalHostProvider remoteSocketProvider) {
    super(process, commandLine, charset);
    myRemoteSocketProvider = remoteSocketProvider;

    myPathMapper = pathMapper != null ? pathMapper : new PyRemotePathMapper();

    putUserData(PythonRemoteInterpreterManager.PATH_MAPPING_SETTINGS_KEY, pathMapper);
  }

  @NotNull
  @Override
  public PathMapper getMappingSettings() {
    return myPathMapper;
  }

  @Override
  public Pair<String, Integer> getRemoteSocket(int localPort) throws RemoteSdkException {
    try {
      return myRemoteSocketProvider.getRemoteSocket(localPort);
    }
    catch (RemoteSdkException e) {
      throw new RuntimeException(e);
    }
  }

  @Nullable
  @Override
  public HostAndPort getLocalTunnel(int remotePort) {
    return myProcess.getLocalTunnel(remotePort);
  }

  @Override
  public boolean canKillProcess() {
    return true;
  }


  @Override
  public void killProcess() {
    destroyProcessImpl();
  }


  @NotNull
  public static PyRemoteProcessHandler createProcessHandler(@NotNull RemoteProcess remoteProcess,
                                                            @NotNull RemoteSdkCredentials data,
                                                            @NotNull GeneralCommandLine commandLine,
                                                            @Nullable PyRemotePathMapper pathMapper,
                                                            @NotNull PyRemoteSocketToLocalHostProvider remoteSocketProvider)
    throws RemoteSdkException {
    return createProcessHandler(remoteProcess, data.getFullInterpreterPath(), commandLine, pathMapper,
                                remoteSocketProvider);
  }

  @NotNull
  public static PyRemoteProcessHandler createProcessHandler(@NotNull RemoteProcess remoteProcess,
                                                            @Nullable String fullInterpreterPath,
                                                            @NotNull GeneralCommandLine commandLine,
                                                            @Nullable PyRemotePathMapper pathMapper,
                                                            @NotNull PyRemoteSocketToLocalHostProvider remoteSocketProvider)
    throws RemoteSdkException {
    return new PyRemoteProcessHandler(remoteProcess,
                                      commandLine.getCommandLineString(fullInterpreterPath), commandLine.getCharset(),
                                      pathMapper, remoteSocketProvider);
  }

  @NotNull
  public static PyRemoteProcessHandler createProcessHandler(@NotNull RemoteProcess remoteProcess,
                                                            @NotNull String commandLine,
                                                            @NotNull Charset charset,
                                                            @Nullable PyRemotePathMapper pathMapper,
                                                            @NotNull PyRemoteSocketToLocalHostProvider remoteSocketProvider) {
    return new PyRemoteProcessHandler(remoteProcess, commandLine, charset, pathMapper, remoteSocketProvider);
  }

  @Override
  public void coloredTextAvailable(@NotNull String text, @NotNull Key outputType) {
    if (text.startsWith(LOG_PY_CHARM_FILE_MAPPING)) {
      text = text.substring(LOG_PY_CHARM_FILE_MAPPING.length());
      String[] paths = text.split("\t");
      if (paths.length == 2) {
        myFileMappings.add(new PathMappingSettings.PathMapping(paths[0].trim(), paths[1].trim()));
      }
      else {
        LOG.warn("Can't parse remote file mapping " + text);
      }
    }
    else {
      super.coloredTextAvailable(text, outputType);
    }
  }

  @Override
  public List<PathMappingSettings.PathMapping> getFileMappings() {
    return myFileMappings;
  }

  @Override
  public @NotNull PyPositionConverter createPositionConverter(@NotNull PyDebugProcess debugProcess) {
    return new PyRemotePositionConverter(debugProcess, myPathMapper);
  }

  @Override
  public PyRemoteSocketToLocalHostProvider getRemoteSocketToLocalHostProvider() {
    return myRemoteSocketProvider;
  }
}
