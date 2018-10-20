// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.arrangement;

import com.google.common.util.concurrent.Futures;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessRunner;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.OSProcessUtil;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

public class ProcessInfoUtil {
  private static final Logger LOG = Logger.getInstance(ProcessInfoUtil.class);
  private static final int TIMEOUT_MILLIS = 1000;

  private ProcessInfoUtil() {}

  @NotNull
  public static Future<String> getWorkingDirectory(@NotNull Process process) {
    if (process.isAlive()) {
      try {
        int pid = OSProcessUtil.getProcessID(process);
        String fastWorkingDir = tryGetWorkingDirFast(pid);
        if (fastWorkingDir != null) {
          return Futures.immediateFuture(fastWorkingDir);
        }
        FutureTask<String> future = new FutureTask<>(() -> doGetWorkingDirectory(pid));
        ApplicationManager.getApplication().executeOnPooledThread(future);
        return future;
      }
      catch (Exception e) {
        LOG.warn("Cannot get pid for " + process);
      }
    }
    return Futures.immediateFuture(null);
  }

  @Nullable
  private static String doGetWorkingDirectory(int pid) throws ExecutionException {
    if (!SystemInfo.isUnix) {
      return null;
    }
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

  @Nullable
  private static String tryGetWorkingDirFast(int pid) {
    if (SystemInfo.isUnix) {
      String procPath = "/proc/" + pid + "/cwd";
      try {
        Path path = Paths.get(procPath).toRealPath();
        if (Files.isDirectory(path)) {
          return path.toString();
        }
      }
      catch (Exception e) {
        LOG.debug("Cannot get working directory from /proc/" + pid + "/cwd", e);
      }
    }
    return null;
  }
}
