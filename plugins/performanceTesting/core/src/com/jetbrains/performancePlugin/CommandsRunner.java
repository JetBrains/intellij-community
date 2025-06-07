// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin;

import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NotNull;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.util.concurrent.CompletableFuture;

public final class CommandsRunner {
  private static CommandsRunner myCommandsRunner;

  private CompletableFuture<?> actionCallback;

  static synchronized CommandsRunner getInstance() {
    if (myCommandsRunner == null) {
      myCommandsRunner = new CommandsRunner();
    }
    return myCommandsRunner;
  }

  //TODO: add methode with project in parameter? AT-114
  public static void doRunScript(@NotNull String text) {
    ProjectLoadedKt.runPerformanceScript(ProjectManager.getInstance().getOpenProjects()[0], text, false);
  }

  public static void setActionCallback(CompletableFuture<?> actionCallback) {
    getInstance().actionCallback = actionCallback;
  }

  public static boolean haveCommandsFinished() {
    if (getInstance().actionCallback == null) {
      return false;
    }
    return getInstance().actionCallback.isDone();
  }

  public static boolean haveCommandsFinishedSuccessfully() {
    return haveCommandsFinished();
  }

  public static boolean haveCommandsFailed() {
    return getInstance().actionCallback.isCancelled();
  }

  public static int getPid() {
    OperatingSystem os = new SystemInfo().getOperatingSystem();
    OSProcess myProc = os.getProcess(os.getProcessId());
    return myProc.getProcessID();
  }
}