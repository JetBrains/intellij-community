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

  private ActionCallback startActionCallback;

  synchronized static CommandsRunner getInstance() {
    if (myCommandsRunner == null) {
      myCommandsRunner = new CommandsRunner();
    }
    return myCommandsRunner;
  }

  //TODO: add methode with project in parameter? AT-114
  public static void doRunScript(@NotNull String text) {
    runScript(ProjectManager.getInstance().getOpenProjects()[0], text.replace("~", "\r\n"));
  }

  public static void setStartActionCallback(ActionCallback actionCallback) {
    getInstance().startActionCallback = actionCallback;
  }

  public static boolean areCommandsAfterStartFinished() {
    if (getInstance().startActionCallback == null) return false;
    return getInstance().startActionCallback.isDone();
  }

  public static int getPid() {
    SystemInfo si = new SystemInfo();
    OperatingSystem os = si.getOperatingSystem();
    OSProcess myProc = os.getProcess(os.getProcessId());
    return myProc.getProcessID();
  }
}
