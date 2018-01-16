package org.jetbrains.plugins.ruby.ruby.actions;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@State(name = "RunAnythingCache", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class RunAnythingCache implements PersistentStateComponent<RunAnythingCache.State> {
  private final State mySettings = new State();

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

  public static class State {
    @NotNull
    public List<String> undefinedCommands = ContainerUtil.newArrayList();
  }
}