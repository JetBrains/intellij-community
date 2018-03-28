package org.jetbrains.plugins.ruby.ruby.actions;

import com.intellij.execution.Executor;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ChooseRunConfigurationPopup;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class RunAnythingRunConfigurationItem extends RunAnythingItem<ChooseRunConfigurationPopup.ItemWrapper> {
  @NotNull private final ChooseRunConfigurationPopup.ItemWrapper myWrapper;

  public RunAnythingRunConfigurationItem(@NotNull ChooseRunConfigurationPopup.ItemWrapper wrapper) {
    myWrapper = wrapper;
  }

  @Override
  public void run(@NotNull Project project,
                  @NotNull Executor executor,
                  @Nullable AnActionEvent event,
                  @Nullable VirtualFile workDirectory,
                  @Nullable Component focusOwner) {
    super.run(project, executor, event, workDirectory, focusOwner);

    Object value = myWrapper.getValue();
    if (value instanceof RunnerAndConfigurationSettings) {
      Executor runExecutor = RunAnythingUtil.findExecutor((RunnerAndConfigurationSettings)value);
      if (runExecutor != null) {
        myWrapper.perform(project, runExecutor, DataManager.getInstance().getDataContext(focusOwner));
      }
    }
  }

  @NotNull
  @Override
  public String getText() {
    return myWrapper.getText();
  }

  @Override
  public void triggerUsage() {
    RunAnythingUtil.triggerDebuggerStatistics();
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return ObjectUtils.notNull(myWrapper.getIcon(), AllIcons.RunConfigurations.Unknown);
  }

  @NotNull
  @Override
  public ChooseRunConfigurationPopup.ItemWrapper getValue() {
    return myWrapper;
  }

  @NotNull
  @Override
  public Component getComponent(boolean isSelected) {
    return RunAnythingUtil.getRunConfigurationCellRendererComponent(myWrapper, isSelected);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RunAnythingRunConfigurationItem item = (RunAnythingRunConfigurationItem)o;
    return Objects.equals(myWrapper, item.myWrapper);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myWrapper);
  }
}