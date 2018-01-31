package org.jetbrains.plugins.ruby.ruby.actions.groups;

import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.actions.ChooseRunConfigurationPopup;
import com.intellij.execution.actions.ExecutorProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.actions.RunAnythingAction;
import org.jetbrains.plugins.ruby.ruby.actions.RunAnythingRunConfigurationItem;
import org.jetbrains.plugins.ruby.ruby.actions.RunAnythingSearchListModel;

public abstract class RunAnythingRunConfigurationGroup extends RunAnythingGroup {
  private static final int MAX_RUN_CONFIGURATION = 6;

  @Override
  protected int getMax() {
    return MAX_RUN_CONFIGURATION;
  }

  @Override
  public RunAnythingAction.SearchResult getItems(@NotNull Project project,
                                                 @Nullable Module module,
                                                 @NotNull RunAnythingSearchListModel listModel,
                                                 @NotNull String pattern,
                                                 boolean isMore, @NotNull Runnable check) {

    final ChooseRunConfigurationPopup.ItemWrapper[] wrappers =
      ChooseRunConfigurationPopup.createSettingsList(project, new ExecutorProvider() {
        @Override
        public Executor getExecutor() {
          return ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.RUN);
        }
      }, false);

    check.run();
    RunAnythingAction.SearchResult result = new RunAnythingAction.SearchResult();
    for (ChooseRunConfigurationPopup.ItemWrapper wrapper : wrappers) {
      if (!isTemporary(wrapper)) continue;

      RunAnythingRunConfigurationItem runConfigurationItem = new RunAnythingRunConfigurationItem(wrapper);
      if (addToList(listModel, result, pattern, runConfigurationItem, runConfigurationItem.getText(), isMore)) break;
      check.run();
    }

    return result;
  }

  protected abstract boolean isTemporary(@NotNull ChooseRunConfigurationPopup.ItemWrapper wrapper);
}