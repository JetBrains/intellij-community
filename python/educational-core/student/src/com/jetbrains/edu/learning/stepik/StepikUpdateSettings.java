package com.jetbrains.edu.learning.stepik;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.Nullable;

@State(name = "StepikUpdateSettings", storages = @Storage("other.xml"))
public class StepikUpdateSettings implements PersistentStateComponent<StepikUpdateSettings> {
  public long LAST_TIME_CHECKED = 0;

  public StepikUpdateSettings() {

  }

  public long getLastTimeChecked() {
    return LAST_TIME_CHECKED;
  }

  public void setLastTimeChecked(long timeChecked) {
    LAST_TIME_CHECKED = timeChecked;
  }

  @Nullable
  @Override
  public StepikUpdateSettings getState() {
    return this;
  }

  @Override
  public void loadState(StepikUpdateSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static StepikUpdateSettings getInstance() {
    return ServiceManager.getService(StepikUpdateSettings.class);
  }
}
