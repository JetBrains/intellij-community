package org.jetbrains.plugins.ruby.ruby.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.ruby.actions.groups.RunAnythingGroup;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

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

  /**
   * @return true is group is visible; false if it's hidden
   */
  public boolean isGroupVisible(@NotNull String key) {
    return mySettings.keys.get(key);
  }

  /**
   * Saves group visibility flag
   * @param key to store visibility flag
   * @param visible true if group should be shown
   */
  public void saveGroupVisibilityKey(@NotNull String key, boolean visible) {
    mySettings.keys.put(key, visible);
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
    @NotNull public Map<String, Boolean> keys = ContainerUtil.newHashMap();
    @NotNull public List<String> undefinedCommands = ContainerUtil.newArrayList();

    public State() {
      Arrays.stream(RunAnythingGroup.EP_NAME.getExtensions()).forEach(group -> keys.put(group.getVisibilityKey(), true));
    }
  }
}