package org.jetbrains.plugins.ruby.ruby.actions.groups;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ChooseRunConfigurationPopup;
import org.jetbrains.annotations.NotNull;

public class RunAnythingTemporaryRunConfigurationGroup extends RunAnythingRunConfigurationGroup {
  @NotNull
  @Override
  public String getTitle() {
    return "Temporary Configurations";
  }

  @NotNull
  @Override
  protected String getKey() {
    return "run.anything.temporary.configurations";
  }

  @NotNull
  @Override
  public WidgetID getWidget() {
    return WidgetID.TEMPORARY;
  }

  @Override
  public boolean isRecent() {
    return true;
  }

  @Override
  protected boolean isTemporary(@NotNull ChooseRunConfigurationPopup.ItemWrapper wrapper) {
    Object value = wrapper.getValue();
    return value instanceof RunnerAndConfigurationSettings && ((RunnerAndConfigurationSettings)value).isTemporary();
  }
}