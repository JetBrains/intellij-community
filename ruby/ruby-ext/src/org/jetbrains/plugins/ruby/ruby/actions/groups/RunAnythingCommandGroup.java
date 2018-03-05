package org.jetbrains.plugins.ruby.ruby.actions.groups;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.RBundle;
import org.jetbrains.plugins.ruby.ruby.actions.*;

public class RunAnythingCommandGroup extends RunAnythingGroup {
  private static final int MAX_COMMANDS = 5;

  @NotNull
  @Override
  public String getTitle() {
    return RBundle.message("run.anything.group.title.commands");
  }

  @NotNull
  @Override
  public String getVisibilityKey() {
    return "run.anything.settings.commands";
  }

  @Override
  protected int getMaxItemsToShow() {
    return MAX_COMMANDS;
  }

  public RunAnythingAction.SearchResult getItems(@NotNull Project project,
                                                 @Nullable Module module,
                                                 @NotNull RunAnythingSearchListModel listModel,
                                                 @NotNull String pattern,
                                                 boolean isMore,
                                                 @NotNull Runnable check) {
    RunAnythingAction.SearchResult result = new RunAnythingAction.SearchResult();

    check.run();
    for (String command : ContainerUtil.iterateBackward(RunAnythingCache.getInstance(project).getState().getCommands())) {
      if (addToList(listModel, result, pattern, new RunAnythingCommandItem(project, module, command), command, isMore)) break;
      check.run();
    }
    return result;
  }

  @Override
  public boolean isRecent() {
    return true;
  }
}