package com.jetbrains.edu.learning.stepic;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.Nullable;

@State(name = "StepicUpdateSettings", storages = @Storage("other.xml"))
public class StepicUpdateSettings implements PersistentStateComponent<StepicUpdateSettings> {
  public long LAST_TIME_CHECKED = 0;

  public StepicUpdateSettings() {

  }

  public long getLastTimeChecked() {
    return LAST_TIME_CHECKED;
  }

  public void setLastTimeChecked(long timeChecked) {
    LAST_TIME_CHECKED = timeChecked;
  }

  @Nullable
  @Override
  public StepicUpdateSettings getState() {
    return this;
  }

  @Override
  public void loadState(StepicUpdateSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static StepicUpdateSettings getInstance() {
    return ServiceManager.getService(StepicUpdateSettings.class);
  }
}
