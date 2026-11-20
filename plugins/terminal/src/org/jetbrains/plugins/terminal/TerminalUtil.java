// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remote.RemoteSshProcess;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence;
import com.intellij.util.containers.ContainerUtil;
import com.jediterm.core.input.KeyEvent;
import com.jediterm.terminal.ProcessTtyConnector;
import com.jediterm.terminal.TerminalStarter;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.unix.UnixPtyProcess;
import com.pty4j.windows.conpty.WinConPtyProcess;
import com.pty4j.windows.winpty.WinPtyProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.util.TerminalEelProcessesKt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class TerminalUtil {

  private static final Logger LOG = Logger.getInstance(TerminalUtil.class);

  public static final String TERMINAL_CONFIGURABLE_ID = "terminal";

  private TerminalUtil() {}

  /**
   * Determines if any command is running in the terminal by checking if the shell has any child processes.
   * This method may access the file system and launch external processes,
   * so it is prohibited to call it on EDT or under read action.
   */
  @SuppressWarnings("UsagesOfObsoleteApi")  // Can't use just only annotations because they generate throwing assertions
  @RequiresReadLockAbsence(generateAssertion = false)
  @RequiresBackgroundThread(generateAssertion = false)
  public static boolean hasRunningCommands(@NotNull TtyConnector connector) throws IllegalStateException {
    ThreadingAssertions.softAssertBackgroundThread();
    ThreadingAssertions.softAssertNoReadAccess();

    if (!connector.isConnected()) return false;
    ProcessTtyConnector processTtyConnector = ShellTerminalWidget.getProcessTtyConnector(connector);
    if (processTtyConnector == null) return true;
    Process javaProcess = processTtyConnector.getProcess();
    Boolean localResult = localProcessHasRunningCommands(javaProcess);
    if (localResult != null) return localResult;
    if (processTtyConnector instanceof LocalTerminalTtyConnector localConnector) {
      return TerminalEelProcessesKt.hasRunningCommandsBlocking(localConnector.getShellEelProcess());
    }
    if (javaProcess instanceof RemoteSshProcess) {
      // No way to determine if `RemoteSshProcess` has child processes.
      // However, SSH processes spawned via EelApi are covered by
      // `org.jetbrains.plugins.terminal.util.TerminalEelProcessesKt.hasRunningCommands`
      return true;
    }
    LOG.warn("Cannot determine if there are running processes: " + SystemInfoRt.OS_NAME + ", " + javaProcess.getClass().getName());
    return false;
  }

  /// Replace with [TerminalEelProcessesKt#hasRunningCommands] once it supports Windows.
  private static @Nullable Boolean localProcessHasRunningCommands(@NotNull Process process) throws IllegalStateException {
    if (process instanceof UnixPtyProcess) {
      return ProcessHandle.of(process.pid()).map(handle -> handle.children().findAny().isPresent()).orElse(false);
    }
    if (process instanceof WinPtyProcess winPty) {
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
    return null;
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
