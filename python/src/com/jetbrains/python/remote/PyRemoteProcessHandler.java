// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.remote;

import com.google.common.net.HostAndPort;
import com.intellij.execution.KillableProcess;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.remote.BaseRemoteProcessHandler;
import com.intellij.remote.RemoteProcess;
import com.intellij.remote.RemoteSdkCredentials;
import com.intellij.remote.RemoteSdkException;
import com.intellij.util.PathMapper;
import com.intellij.util.PathMappingSettings;
import com.intellij.util.io.BaseOutputReader;
import com.jetbrains.python.debugger.PyDebugProcess;
import com.jetbrains.python.debugger.PyPositionConverter;
import com.jetbrains.python.debugger.remote.vfs.PyRemotePositionConverter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public final class PyRemoteProcessHandler extends BaseRemoteProcessHandler<RemoteProcess>
  implements RemoteDebuggableProcessHandler, KillableProcess, AnsiEscapeDecoder.ColoredTextAcceptor {
  private static final Logger LOG = Logger.getInstance(PyRemoteProcessHandler.class);

  private final AnsiEscapeDecoder myAnsiEscapeDecoder = new AnsiEscapeDecoder();
  public static final String LOG_PY_CHARM_FILE_MAPPING = "LOG: PyCharm: File mapping:";
  @NotNull private final PyRemotePathMapper myPathMapper;
  private final List<PathMappingSettings.PathMapping> myFileMappings = new ArrayList<>();
  @NotNull private final PyRemoteSocketToLocalHostProvider myRemoteSocketProvider;
  /**
   * Indicates if [myProcess] is launched with PTY.
   * It changes the logic of readerOptions, so it can break the output if the [myProcess] has been launched without PTY and that value is
   * set as true.
   * Look at PY-60900, PY-55322.
   */
  private final boolean myIsRunWithPty;

  private PyRemoteProcessHandler(@NotNull RemoteProcess process,
                                 @NotNull String commandLine,
                                 @NotNull Charset charset,
                                 @Nullable PyRemotePathMapper pathMapper,
                                 @NotNull PyRemoteSocketToLocalHostProvider remoteSocketProvider,
                                 boolean isRunWithPty) {
    super(process, commandLine, charset);
    myRemoteSocketProvider = remoteSocketProvider;

    myPathMapper = pathMapper != null ? pathMapper : new PyRemotePathMapper();
    myIsRunWithPty = isRunWithPty;
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
                                      pathMapper, remoteSocketProvider, false);
  }

  @NotNull
  public static PyRemoteProcessHandler createProcessHandler(@NotNull RemoteProcess remoteProcess,
                                                            @NotNull String commandLine,
                                                            @NotNull Charset charset,
                                                            @Nullable PyRemotePathMapper pathMapper,
                                                            @NotNull PyRemoteSocketToLocalHostProvider remoteSocketProvider,
                                                            boolean isRunWithPty) {
    return new PyRemoteProcessHandler(remoteProcess, commandLine, charset, pathMapper, remoteSocketProvider, isRunWithPty);
  }

  @NotNull
  public static PyRemoteProcessHandler createProcessHandler(@NotNull RemoteProcess remoteProcess,
                                                            @NotNull String commandLine,
                                                            @NotNull Charset charset,
                                                            @Nullable PyRemotePathMapper pathMapper,
                                                            @NotNull PyRemoteSocketToLocalHostProvider remoteSocketProvider) {
    return createProcessHandler(remoteProcess, commandLine, charset, pathMapper, remoteSocketProvider, false);
  }

  @Override
  public void notifyTextAvailable(@NotNull String text, @NotNull Key outputType) {
    if (isRunWithPty()) {
      boolean foundPyCharmFileMapping = handlePyCharmFileMapping(text);
      if (!foundPyCharmFileMapping) {
        super.notifyTextAvailable(text, outputType);
      }
    } else {
      myAnsiEscapeDecoder.escapeText(text, outputType, this);
    }
  }

  /**
   * Processes the PyCharm file mapping provided in the text.
   *
   * @return true if the text contains valid PyCharm file mapping, else false.
   */
  private boolean handlePyCharmFileMapping(@NotNull String text) {
    if (text.startsWith(LOG_PY_CHARM_FILE_MAPPING)) {
      text = text.substring(LOG_PY_CHARM_FILE_MAPPING.length());
      String[] paths = text.split("\t");
      if (paths.length == 2) {
        myFileMappings.add(new PathMappingSettings.PathMapping(paths[0].trim(), paths[1].trim()));
      }
      else {
        LOG.warn("Can't parse remote file mapping " + text);
      }
      return true;
    }
    return false;
  }

  @Override
  public void coloredTextAvailable(@NotNull String text, @NotNull Key outputType) {
    boolean foundPyCharmFileMapping = handlePyCharmFileMapping(text);
    if (!foundPyCharmFileMapping) {
      super.notifyTextAvailable(text, outputType);
    }
  }

  public boolean isRunWithPty() {
    return myIsRunWithPty;
  }

  @Override
  protected BaseOutputReader.@NotNull Options readerOptions() {
    if (isRunWithPty()) {
      return BaseOutputReader.Options.forTerminalPtyProcess();
    }
    return super.readerOptions();
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
