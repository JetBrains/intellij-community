package org.jetbrains.plugins.ruby.ruby.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@State(name = "RunAnythingCache", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class RunAnythingCache implements PersistentStateComponent<RunAnythingCache.State> {
  private final State mySettings = new State();
  public boolean CAN_RUN_RVM = false;
  public boolean CAN_RUN_RBENV = false;

  public RunAnythingCache() {
    try {
      CAN_RUN_RVM = ApplicationManager.getApplication().executeOnPooledThread(() -> canRunRVM()).get();
    }
    catch (InterruptedException | java.util.concurrent.ExecutionException ignored) {
    }

    try {
      CAN_RUN_RBENV = ApplicationManager.getApplication().executeOnPooledThread(() -> canRunRbenv()).get();
    }
    catch (InterruptedException | java.util.concurrent.ExecutionException ignored) {
    }
  }

  public static RunAnythingCache getInstance(Project project) {
    return ServiceManager.getService(project, RunAnythingCache.class);
  }

  @NotNull
  @Override
  public State getState() {
    return mySettings;
  }

  @Override
  public void loadState(@NotNull State state) {
    XmlSerializerUtil.copyBean(state, mySettings);
  }

  static boolean canRunRbenv() {
    return canRunCommand("rbenv");
  }

  static boolean canRunRVM() {
    return canRunCommand("rvm");
  }

  private static boolean canRunCommand(@NotNull String command) {
    GeneralCommandLine generalCommandLine = new GeneralCommandLine(command);
    generalCommandLine.withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
    try {
      generalCommandLine.createProcess();
    }
    catch (ExecutionException e) {
      return false;
    }
    return true;
  }

  public static class State {
    @NotNull
    public List<String> undefinedCommands = ContainerUtil.newArrayList();
  }
}