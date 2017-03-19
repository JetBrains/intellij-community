package com.jetbrains.edu.learning.stepic;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "StepicUpdateSettings", storages = @Storage("other.xml"))
public class StepicUpdateSettings implements PersistentStateComponent<StepicUpdateSettings> {
  private StepicUser myUser;
  public long LAST_TIME_CHECKED = 0;
  private boolean myEnableTestingFromSamples = false;

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
  @NotNull
  public StepicUser getUser() {
    if (myUser == null) {
      myUser = new StepicUser();
    }
    return myUser;
  }

  public void setUser(@NotNull final StepicUser user) {
    myUser = user;
  }

  public boolean isEnableTestingFromSamples() {
    return myEnableTestingFromSamples;
  }

  public void setEnableTestingFromSamples(boolean enableTestingFromSamples) {
    myEnableTestingFromSamples = enableTestingFromSamples;
  }
}
