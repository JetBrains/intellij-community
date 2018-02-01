package org.jetbrains.plugins.ruby.ruby.actions.groups;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ChooseRunConfigurationPopup;
import org.jetbrains.annotations.NotNull;

public class RunAnythingPermanentRunConfigurationGroup extends RunAnythingRunConfigurationGroup {
  @NotNull
  @Override
  public String getTitle() {
    return "Permanent configurations";
  }

  @NotNull
  @Override
  protected String getSettingsKey() {
    return "run.anything.settings.permanent.configurations";
  }

  @Override
  protected boolean isTemporary(@NotNull ChooseRunConfigurationPopup.ItemWrapper wrapper) {
    Object value = wrapper.getValue();
    return !(value instanceof RunnerAndConfigurationSettings && ((RunnerAndConfigurationSettings)value).isTemporary());
  }
}