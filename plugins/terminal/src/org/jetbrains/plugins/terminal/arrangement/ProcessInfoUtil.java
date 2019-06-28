// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.arrangement;

import com.google.common.util.concurrent.Futures;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessRunner;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.OSProcessUtil;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.pty4j.windows.WinPtyProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class ProcessInfoUtil {
  private static final Logger LOG = Logger.getInstance(ProcessInfoUtil.class);
  private static final int TIMEOUT_MILLIS = 2000;
  // restrict amount of concurrent cwd fetchings to not utilize all the threads in case of unpredicted hangings
  private static final ExecutorService POOL = AppExecutorUtil.createBoundedScheduledExecutorService("Terminal CWD", 1);

  private ProcessInfoUtil() {}

  @NotNull
  public static Future<String> getCurrentWorkingDirectory(@NotNull Process process) {
    if (process.isAlive()) {
      return POOL.submit(() -> doGetCwd(process));
    }
    return Futures.immediateFuture(null);
  }

  @Nullable
  private static String doGetCwd(@NotNull Process process) throws Exception {
    if (SystemInfo.isUnix) {
      int pid = OSProcessUtil.getProcessID(process);
      String result = tryGetCwdFastOnUnix(pid);
      if (result != null) {
        return result;
      }
      return getCwdOnUnix(pid);
    }
    else if (SystemInfo.isWindows) {
      if (process instanceof WinPtyProcess) {
        return ((WinPtyProcess)process).getWorkingDirectory();
      }
      throw new IllegalStateException("Cwd can be fetched for " + WinPtyProcess.class + " only, got " + process.getClass());
    }
    throw new IllegalStateException("Unsupported OS: " + SystemInfo.OS_NAME);
  }

  @Nullable
  private static String tryGetCwdFastOnUnix(int pid) {
    String procPath = "/proc/" + pid + "/cwd";
    try {
      File dir = Paths.get(procPath).toRealPath().toFile();
      if (dir != null && dir.isDirectory()) {
        return dir.getAbsolutePath();
      }
    }
    catch (Exception e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Cannot resolve cwd from " + procPath + ", fallback to lsof -a -d cwd -p " + pid, e);
      }
    }
    return null;
  }

  @NotNull
  private static String getCwdOnUnix(int pid) throws ExecutionException {
    GeneralCommandLine commandLine = new GeneralCommandLine("lsof", "-a", "-d", "cwd", "-p", String.valueOf(pid), "-Fn");
    CapturingProcessRunner runner = new CapturingProcessRunner(new OSProcessHandler(commandLine));
    ProcessOutput output = runner.runProcess(TIMEOUT_MILLIS);
    if (output.isTimeout()) {
      throw new ExecutionException("Timeout running " + commandLine.getCommandLineString());
    }
    if (output.getExitCode() != 0) {
      throw new ExecutionException("Exit code " + output.getExitCode() + " for " + commandLine.getCommandLineString());
    }
    String workingDir = parseWorkingDirectory(output.getStdoutLines(), pid);
    if (workingDir == null) {
      throw new ExecutionException("Cannot parse working directory from " + commandLine.getCommandLineString());
    }
    return workingDir;
  }

  @Nullable
  private static String parseWorkingDirectory(@NotNull List<String> stdoutLines, int pid) {
    boolean pidEncountered = false;
    for (String line : stdoutLines) {
      if (line.startsWith("p")) {
        int p = StringUtil.parseInt(line.substring(1), -1);
        pidEncountered |= p == pid;
      }
      else if (pidEncountered && line.startsWith("n")) {
        return line.substring(1);
      }
    }
    return null;
  }
}
