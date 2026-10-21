// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin;

import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NotNull;

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

  /**
   * Returns whether the commands of the startup script have finished. Before the script starts, the commands
   * are finished only when the script has none.
   */
  public static boolean haveCommandsFinished() {
    CompletableFuture<?> actionCallback = getInstance().actionCallback;
    if (actionCallback == null) {
      return !ProjectLoadedKt.hasStartupScriptCommands();
    }
    return actionCallback.isDone();
  }

  public static boolean haveCommandsFinishedSuccessfully() {
    return haveCommandsFinished();
  }

  public static boolean haveCommandsFailed() {
    CompletableFuture<?> actionCallback = getInstance().actionCallback;
    return actionCallback != null && actionCallback.isCompletedExceptionally();
  }

  public static int getPid() {
    return Math.toIntExact(ProcessHandle.current().pid());
  }
}