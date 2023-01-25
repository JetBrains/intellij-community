// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.navigator.actions;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

@State(name = "mavenExecuteGoalHistory", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
@Service(Service.Level.PROJECT)
public final class ExecuteMavenGoalHistoryService implements PersistentStateComponent<String[]> {
  private static final int MAX_HISTORY_LENGTH = 20;

  private final LinkedList<String> myHistory = new LinkedList<>();

  private String myWorkDirectory = "";

  private String myCanceledCommand;

  public static ExecuteMavenGoalHistoryService getInstance(@NotNull Project project) {
    return project.getService(ExecuteMavenGoalHistoryService.class);
  }

  @Nullable
  public String getCanceledCommand() {
    return myCanceledCommand;
  }

  public void setCanceledCommand(@Nullable String canceledCommand) {
    myCanceledCommand = canceledCommand;
  }

  public void addCommand(@NotNull String command, @NotNull String projectPath) {
    myWorkDirectory = projectPath.trim();

    command = command.trim();

    if (command.length() == 0) return;

    myHistory.remove(command);
    myHistory.addFirst(command);

    while (myHistory.size() > MAX_HISTORY_LENGTH) {
      myHistory.removeLast();
    }
  }

  public List<String> getHistory() {
    return new ArrayList<>(myHistory);
  }

  @NotNull
  public String getWorkDirectory() {
    return myWorkDirectory;
  }

  @Override
  public String @Nullable [] getState() {
    String[] res = new String[myHistory.size() + 1];
    res[0] = myWorkDirectory;

    int i = 1;
    for (String goal : myHistory) {
      res[i++] = goal;
    }

    return res;
  }

  @Override
  public void loadState(String @NotNull [] state) {
    if (state.length == 0) {
      myWorkDirectory = "";
      myHistory.clear();
    }
    else {
      myWorkDirectory = state[0];
      myHistory.addAll(Arrays.asList(state).subList(1, state.length));
    }
  }
}
