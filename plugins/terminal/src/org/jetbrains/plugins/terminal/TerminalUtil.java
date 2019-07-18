// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.execution.process.OSProcessUtil;
import com.intellij.execution.process.UnixProcessManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.remote.RemoteSshProcess;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.execution.ParametersListUtil;
import com.jediterm.terminal.ProcessTtyConnector;
import com.pty4j.unix.UnixPtyProcess;
import com.pty4j.windows.WinPtyProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jvnet.winp.WinProcess;
import org.jvnet.winp.WinpException;

import java.io.IOException;
import java.util.StringTokenizer;

public class TerminalUtil {

  private static final Logger LOG = Logger.getInstance(TerminalUtil.class);

  private TerminalUtil() {}

  @NotNull
  public static JBTerminalWidget createTerminal(@NotNull AbstractTerminalRunner terminalRunner,
                                                @Nullable TerminalTabState tabState,
                                                @Nullable Disposable parentDisposable) {
    VirtualFile currentWorkingDir = getCurrentWorkingDir(tabState);
    if (parentDisposable == null) {
      parentDisposable = Disposer.newDisposable();
    }
    return terminalRunner.createTerminalWidget(parentDisposable, currentWorkingDir);
  }

  @Nullable
  private static VirtualFile getCurrentWorkingDir(@Nullable TerminalTabState tabState) {
    String dir = tabState != null ? tabState.myWorkingDirectory : null;
    VirtualFile result = null;
    if (dir != null) {
      result = LocalFileSystem.getInstance().findFileByPath(dir);
    }
    return result;
  }

  public static boolean hasRunningCommands(@NotNull ProcessTtyConnector connector) throws IllegalStateException {
    Process process = connector.getProcess();
    if (!process.isAlive()) return false;
    if (process instanceof RemoteSshProcess) return true;
    if (SystemInfo.isUnix && process instanceof UnixPtyProcess) {
      int shellPid = OSProcessUtil.getProcessID(process);
      MultiMap<Integer, Integer> pidToChildPidsMap = MultiMap.create();
      UnixProcessManager.processPSOutput(UnixProcessManager.getPSCmd(false, false), new Processor<String>() {
        @Override
        public boolean process(String s) {
          StringTokenizer st = new StringTokenizer(s, " ");
          int parentPid = Integer.parseInt(st.nextToken());
          int pid = Integer.parseInt(st.nextToken());
          pidToChildPidsMap.putValue(parentPid, pid);
          return false;
        }
      });
      return !pidToChildPidsMap.get(shellPid).isEmpty();
    }
    if (SystemInfo.isWindows && process instanceof WinPtyProcess) {
      WinPtyProcess winPty = (WinPtyProcess)process;
      try {
        String executable = FileUtil.toSystemIndependentName(StringUtil.notNullize(getExecutable(winPty.getChildProcessId())));
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
    throw new IllegalStateException("Cannot determine if there are running processes: " + SystemInfo.OS_NAME +
                                    ", " + process.getClass().getName());
  }

  @Nullable
  private static String getExecutable(int pid) {
    WinProcess winProcess = new WinProcess(pid);
    String commandLine;
    try {
      commandLine = winProcess.getCommandLine();
    }
    catch (WinpException e) {
      LOG.error(e);
      return null;
    }
    return ContainerUtil.getFirstItem(ParametersListUtil.parse(commandLine));
  }
}
