// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remote.RemoteSshProcess;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.system.OS;
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

  private TerminalUtil() { }

  /// Determines if any command is running in the terminal by checking if the shell has any child processes.
  /// This method may access the file system and launch external processes,
  /// so it is prohibited to call it on EDT or under read action.
  @RequiresReadLockAbsence(generateAssertion = false)
  @RequiresBackgroundThread(generateAssertion = false)
  public static boolean hasRunningCommands(@NotNull TtyConnector connector) throws IllegalStateException {
    // Can't use just only annotations because they generate throwing assertions
    ThreadingAssertions.softAssertBackgroundThread();
    ThreadingAssertions.softAssertNoReadAccess();

    if (!connector.isConnected()) return false;
    var processTtyConnector = ShellTerminalWidget.getProcessTtyConnector(connector);
    if (processTtyConnector == null) return true;
    var javaProcess = processTtyConnector.getProcess();
    var localResult = localProcessHasRunningCommands(javaProcess);
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
    LOG.warn("Cannot determine if there are running processes: " + OS.CURRENT + ", " + javaProcess.getClass().getName());
    return false;
  }

  /// Replace with [TerminalEelProcessesKt#hasRunningCommands] once it supports Windows.
  private static @Nullable Boolean localProcessHasRunningCommands(@NotNull Process process) throws IllegalStateException {
    if (process instanceof UnixPtyProcess) {
      return ProcessHandle.of(process.pid()).map(handle -> handle.children().findAny().isPresent()).orElse(false);
    }
    if (process instanceof WinPtyProcess winPty) {
      try {
        var executable = getExecutable(winPty.getCommandWrapper().toList());
        var consoleProcessCount = winPty.getConsoleProcessCount();
        return executable.endsWith("/Git/bin/bash.exe") ? consoleProcessCount > 3 : consoleProcessCount > 2;
      }
      catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
    if (process instanceof WinConPtyProcess conPty) {
      try {
        var executable = getExecutable(conPty.getCommandWrapper().toList());
        var consoleProcessCount = conPty.getConsoleProcessCount();
        return executable.endsWith("/Git/bin/bash.exe") ? consoleProcessCount > 2 : consoleProcessCount > 1;
      }
      catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
    return null;
  }

  private static String getExecutable(List<String> command) {
    return StringUtil.notNullize(ContainerUtil.getFirstItem(command)).replace('\\', '/');
  }

  /// Add the item to the list.
  /// When the `parentDisposable` is disposed, then the item will be removed from the list.
  /// Used to register an item (i.e., a listener) which depends on the `parentDisposable`.
  public static <T> void addItem(@NotNull List<T> items, @NotNull T item, @NotNull Disposable parentDisposable) {
    items.add(item);
    var registered = Disposer.tryRegister(parentDisposable, () -> {
      items.remove(item);
    });
    if (!registered) {
      items.remove(item);
    }
  }

  /// Sends a command to TTY.
  /// Does not wait for running command to finish.
  /// Non-blocking operation.
  public static void sendCommandToExecute(@NotNull String shellCommand, @NotNull TerminalStarter terminalStarter) {
    var result = new StringBuilder();
    if (terminalStarter.isLastSentByteEscape()) {
      result.append((char)KeyEvent.VK_BACK_SPACE); // remove Escape first, workaround for IDEA-221031
    }
    var enterCode = new String(terminalStarter.getTerminal().getCodeForKey(KeyEvent.VK_ENTER, 0), StandardCharsets.UTF_8);
    result.append(shellCommand).append(enterCode);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Sending " + shellCommand);
    }
    terminalStarter.sendString(result.toString(), false);
  }

  /// @deprecated use org.jetbrains.plugins.terminal.TerminalUtil#hasRunningCommands(com.jediterm.terminal.TtyConnector) instead
  @Deprecated(forRemoval = true)
  public static boolean hasRunningCommands(@NotNull ProcessTtyConnector connector) throws IllegalStateException {
    return hasRunningCommands((TtyConnector)connector);
  }
}
