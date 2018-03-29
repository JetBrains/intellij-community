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
import org.jetbrains.plugins.ruby.ruby.actions.RunAnythingRunConfigurationItem;
import org.jetbrains.plugins.ruby.ruby.actions.RunAnythingSearchListModel;

public abstract class RunAnythingRunConfigurationGroup extends RunAnythingGroupBase {
  private static final int MAX_RUN_CONFIGURATION = 6;

  @Override
  protected int getMaxInitialItems() {
    return MAX_RUN_CONFIGURATION;
  }

  @Override
  public SearchResult getItems(@NotNull Project project,
                               @Nullable Module module,
                               @NotNull RunAnythingSearchListModel model, @NotNull String pattern,
                               boolean isInsertionMode, @NotNull Runnable check) {

    final ChooseRunConfigurationPopup.ItemWrapper[] wrappers =
      ChooseRunConfigurationPopup.createSettingsList(project, new ExecutorProvider() {
        @Override
        public Executor getExecutor() {
          return ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.RUN);
        }
      }, false);

    check.run();
    SearchResult result = new SearchResult();
    for (ChooseRunConfigurationPopup.ItemWrapper wrapper : wrappers) {
      if (!isTemporary(wrapper)) continue;

      RunAnythingRunConfigurationItem runConfigurationItem = new RunAnythingRunConfigurationItem(wrapper);
      if (addToList(model, result, pattern, runConfigurationItem, runConfigurationItem.getText(), isInsertionMode)) break;
      check.run();
    }

    return result;
  }

  protected abstract boolean isTemporary(@NotNull ChooseRunConfigurationPopup.ItemWrapper wrapper);
}