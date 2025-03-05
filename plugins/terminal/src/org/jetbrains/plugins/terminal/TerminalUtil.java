// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.execution.process.UnixProcessManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remote.RemoteSshProcess;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.jediterm.core.input.KeyEvent;
import com.jediterm.terminal.ProcessTtyConnector;
import com.jediterm.terminal.TerminalStarter;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.unix.UnixPtyProcess;
import com.pty4j.windows.conpty.WinConPtyProcess;
import com.pty4j.windows.winpty.WinPtyProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.StringTokenizer;

public final class TerminalUtil {

  private static final Logger LOG = Logger.getInstance(TerminalUtil.class);

  private TerminalUtil() {}

  public static boolean hasRunningCommands(@NotNull TtyConnector connector) throws IllegalStateException {
    if (!connector.isConnected()) return false;
    ProcessTtyConnector processTtyConnector = ShellTerminalWidget.getProcessTtyConnector(connector);
    if (processTtyConnector == null) return true;
    return hasRunningCommands(processTtyConnector.getProcess());
  }

  private static boolean hasRunningCommands(@NotNull Process process) throws IllegalStateException {
    if (process instanceof RemoteSshProcess) return true;
    if (SystemInfo.isUnix && process instanceof UnixPtyProcess) {
      int shellPid = (int)process.pid();
      MultiMap<Integer, Integer> pidToChildPidsMap = MultiMap.create();
      UnixProcessManager.processPSOutput(UnixProcessManager.getPSCmd(false, false), s -> {
        StringTokenizer st = new StringTokenizer(s, " ");
        int parentPid = Integer.parseInt(st.nextToken());
        int pid = Integer.parseInt(st.nextToken());
        pidToChildPidsMap.putValue(parentPid, pid);
        return false;
      });
      return !pidToChildPidsMap.get(shellPid).isEmpty();
    }
    if (SystemInfo.isWindows && process instanceof WinPtyProcess winPty) {
      try {
        String executable = FileUtil.toSystemIndependentName(StringUtil.notNullize(getExecutable(winPty)));
        int consoleProcessCount = winPty.getConsoleProcessCount();
        if (executable.endsWith("/Git/bin/bash.exe")) {
          return consoleProcessCount > 3;
        }
        return consoleProcessCount > 2;
      }
      catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
    if (process instanceof WinConPtyProcess conPtyProcess) {
      try {
        String executable = FileUtil.toSystemIndependentName(StringUtil.notNullize(ContainerUtil.getFirstItem(conPtyProcess.getCommand())));
        int consoleProcessCount = conPtyProcess.getConsoleProcessCount();
        if (executable.endsWith("/Git/bin/bash.exe")) {
          return consoleProcessCount > 2;
        }
        return consoleProcessCount > 1;
      }
      catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
    LOG.warn("Cannot determine if there are running processes: " + SystemInfo.OS_NAME + ", " + process.getClass().getName());
    return false;
  }

  private static @Nullable String getExecutable(@NotNull WinPtyProcess process) {
    return ContainerUtil.getFirstItem(process.getCommand());
  }

  /**
   * Add the item to the list.
   * When the `parentDisposable` is disposed,
   * then the item will be removed from the list.
   *
   * Used to register an item (i.e. listener) which depends on the `parentDisposable`.
   */
  public static <T> void addItem(@NotNull List<T> items, @NotNull T item, @NotNull Disposable parentDisposable) {
    items.add(item);
    boolean registered = Disposer.tryRegister(parentDisposable, () -> {
      items.remove(item);
    });
    if (!registered) {
      items.remove(item);
    }
  }

  /**
   * Sends command to TTY.
   * Does not wait for running command to finish.
   * Non-Blocking operation.
   */
  public static void sendCommandToExecute(@NotNull String shellCommand, @NotNull TerminalStarter terminalStarter) {
    StringBuilder result = new StringBuilder();
    if (terminalStarter.isLastSentByteEscape()) {
      result.append((char)KeyEvent.VK_BACK_SPACE); // remove Escape first, workaround for IDEA-221031
    }
    String enterCode = new String(terminalStarter.getTerminal().getCodeForKey(KeyEvent.VK_ENTER, 0), StandardCharsets.UTF_8);
    result.append(shellCommand).append(enterCode);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Sending " + shellCommand);
    }
    terminalStarter.sendString(result.toString(), false);
  }

  /**
   * @deprecated use org.jetbrains.plugins.terminal.TerminalUtil#hasRunningCommands(com.jediterm.terminal.TtyConnector) instead
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public static boolean hasRunningCommands(@NotNull ProcessTtyConnector connector) throws IllegalStateException {
    return hasRunningCommands((TtyConnector)connector);
  }
}
