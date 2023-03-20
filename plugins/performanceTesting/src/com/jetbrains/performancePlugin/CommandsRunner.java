package com.jetbrains.performancePlugin;

import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NotNull;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import static com.jetbrains.performancePlugin.ProjectLoaded.runScript;

public class CommandsRunner {

  private static CommandsRunner myCommandsRunner;

  private ActionCallback actionCallback;

  synchronized static CommandsRunner getInstance() {
    if (myCommandsRunner == null) {
      myCommandsRunner = new CommandsRunner();
    }
    return myCommandsRunner;
  }

  //TODO: add methode with project in parameter? AT-114
  public static void doRunScript(@NotNull String text) {
    runScript(ProjectManager.getInstance().getOpenProjects()[0], text, false);
  }

  public static void setActionCallback(ActionCallback actionCallback) {
    getInstance().actionCallback = actionCallback;
  }

  public static boolean haveCommandsFinished() {
    if (getInstance().actionCallback == null) return false;
    return getInstance().actionCallback.isProcessed();
  }

  public static boolean haveCommandsFinishedSuccessfully() {
    if (getInstance().actionCallback == null) return false;
    return getInstance().actionCallback.isDone();
  }

  public static boolean haveCommandsFailed() {
    return getInstance().actionCallback.isRejected();
  }

  public static int getPid() {
    OperatingSystem os = new SystemInfo().getOperatingSystem();
    OSProcess myProc = os.getProcess(os.getProcessId());
    return myProc.getProcessID();
  }
}