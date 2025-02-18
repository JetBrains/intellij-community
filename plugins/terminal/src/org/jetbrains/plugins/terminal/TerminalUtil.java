// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.UnixProcessManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remote.RemoteSshProcess;
import com.intellij.util.EnvironmentUtil;
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
import org.jetbrains.plugins.terminal.block.TerminalUsageLocalStorage;
import org.jetbrains.plugins.terminal.block.feedback.BlockTerminalFeedbackSurveyKt;
import org.jetbrains.plugins.terminal.fus.BlockTerminalSwitchPlace;
import org.jetbrains.plugins.terminal.fus.TerminalUsageTriggerCollector;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

  /**
   * Detects available shells
   *
   * @param environment the terminal environment variables, as configured in the settings
   * @return the list of available shells based on the OS setup and the provided environment
   */
  static @NotNull List<String> detectShells(Map<String, String> environment) {
    List<String> shells = new ArrayList<>();
    if (SystemInfo.isUnix) {
      addIfExists(shells, "/bin/bash");
      addIfExists(shells, "/usr/bin/bash");
      addIfExists(shells, "/usr/local/bin/bash");
      addIfExists(shells, "/opt/homebrew/bin/bash");

      addIfExists(shells, "/bin/zsh");
      addIfExists(shells, "/usr/bin/zsh");
      addIfExists(shells, "/usr/local/bin/zsh");
      addIfExists(shells, "/opt/homebrew/bin/zsh");

      addIfExists(shells, "/bin/fish");
      addIfExists(shells, "/usr/bin/fish");
      addIfExists(shells, "/usr/local/bin/fish");
      addIfExists(shells, "/opt/homebrew/bin/fish");

      addIfExists(shells, "/opt/homebrew/bin/pwsh");
    }
    else if (SystemInfo.isWindows) {
      File powershell = PathEnvironmentVariableUtil.findInPath("powershell.exe");
      if (powershell != null && StringUtil.startsWithIgnoreCase(powershell.getAbsolutePath(), "C:\\Windows\\System32\\WindowsPowerShell\\")) {
        shells.add(powershell.getAbsolutePath());
      }
      File cmd = PathEnvironmentVariableUtil.findInPath("cmd.exe");
      if (cmd != null && StringUtil.startsWithIgnoreCase(cmd.getAbsolutePath(), "C:\\Windows\\System32\\")) {
        shells.add(cmd.getAbsolutePath());
      }
      File pwsh = PathEnvironmentVariableUtil.findInPath("pwsh.exe");
      if (pwsh != null && StringUtil.startsWithIgnoreCase(pwsh.getAbsolutePath(), "C:\\Program Files\\PowerShell\\")) {
        shells.add(pwsh.getAbsolutePath());
      }
      File gitBash = new File("C:\\Program Files\\Git\\bin\\bash.exe");
      if (gitBash.isFile()) {
        shells.add(gitBash.getAbsolutePath());
      }
      String cmderRoot = EnvironmentUtil.getValue("CMDER_ROOT");
      if (cmderRoot == null) {
        cmderRoot = environment.get("CMDER_ROOT");
      }
      if (cmderRoot != null && cmd != null && StringUtil.startsWithIgnoreCase(cmd.getAbsolutePath(), "C:\\Windows\\System32\\")) {
        shells.add("cmd.exe /k \"%CMDER_ROOT%\\vendor\\init.bat\"");
      }
    }
    return shells;
  }

  private static void addIfExists(@NotNull List<String> shells, @NotNull String filePath) {
    if (Files.exists(Path.of(filePath))) {
      shells.add(filePath);
    }
  }

  static boolean isGenOneTerminalEnabled() {
    return Registry.is(LocalBlockTerminalRunner.BLOCK_TERMINAL_REGISTRY, false);
  }

  static boolean isGenTwoTerminalEnabled() {
    return Registry.is(LocalBlockTerminalRunner.REWORKED_BLOCK_TERMINAL_REGISTRY, false);
  }

  static void setGenOneTerminalEnabled(@NotNull Project project, boolean enabled) {
    var blockTerminalSetting = Registry.get(LocalBlockTerminalRunner.BLOCK_TERMINAL_REGISTRY);
    if (blockTerminalSetting.asBoolean() != enabled) {
      blockTerminalSetting.setValue(enabled);
      TerminalUsageTriggerCollector.triggerBlockTerminalSwitched(project, enabled,
                                                                                   BlockTerminalSwitchPlace.SETTINGS);
      if (!enabled) {
        TerminalUsageLocalStorage.getInstance().recordBlockTerminalDisabled();
        ApplicationManager.getApplication().invokeLater(() -> {
          BlockTerminalFeedbackSurveyKt.showBlockTerminalFeedbackNotification(project);
        }, ModalityState.nonModal());
      }
    }
  }
}
