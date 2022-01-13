// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.execution.process.OSProcessUtil;
import com.intellij.execution.process.UnixProcessManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remote.RemoteSshProcess;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.jediterm.terminal.ProcessTtyConnector;
import com.pty4j.unix.UnixPtyProcess;
import com.pty4j.windows.WinPtyProcess;
import com.pty4j.windows.conpty.WinConPtyProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.StringTokenizer;

public final class TerminalUtil {

  private static final Logger LOG = Logger.getInstance(TerminalUtil.class);

  private TerminalUtil() {}

  public static boolean hasRunningCommands(@NotNull ProcessTtyConnector connector) throws IllegalStateException {
    Process process = connector.getProcess();
    if (!process.isAlive()) return false;
    if (process instanceof RemoteSshProcess) return true;
    if (SystemInfo.isUnix && process instanceof UnixPtyProcess) {
      int shellPid = OSProcessUtil.getProcessID(process);
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
    if (SystemInfo.isWindows && process instanceof WinPtyProcess) {
      WinPtyProcess winPty = (WinPtyProcess)process;
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
    if (process instanceof WinConPtyProcess) {
      WinConPtyProcess conPtyProcess = (WinConPtyProcess)process;
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
}
