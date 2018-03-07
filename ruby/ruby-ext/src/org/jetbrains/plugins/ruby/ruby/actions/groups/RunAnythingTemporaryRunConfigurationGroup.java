package org.jetbrains.plugins.ruby.ruby.actions.groups;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ChooseRunConfigurationPopup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.RBundle;

public class RunAnythingTemporaryRunConfigurationGroup extends RunAnythingRunConfigurationGroup {
  @NotNull
  @Override
  public String getTitle() {
    return RBundle.message("run.anything.group.title.temporary");
  }

  @NotNull
  @Override
  public String getVisibilityKey() {
    return "run.anything.settings.temporary.configurations";
  }

  @Override
  public boolean shouldBeShownInitially() {
    return true;
  }

  @Override
  protected boolean isTemporary(@NotNull ChooseRunConfigurationPopup.ItemWrapper wrapper) {
    Object value = wrapper.getValue();
    return value instanceof RunnerAndConfigurationSettings && ((RunnerAndConfigurationSettings)value).isTemporary();
  }
}