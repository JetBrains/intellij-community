package com.intellij.ide.startup;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;

/**
 * @author mike
 */
public abstract class StartupManagerEx extends StartupManager {
  public abstract boolean startupActivityRunning();
  public abstract boolean startupActivityPassed();
  public abstract void registerPreStartupActivity(Runnable runnable); // should be used only to register to FileSystemSynchronizer!

  public static StartupManagerEx getInstanceEx(Project project) {
    return (StartupManagerEx)getInstance(project);
  }
}
