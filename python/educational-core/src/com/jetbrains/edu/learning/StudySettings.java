package com.jetbrains.edu.learning;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.messages.Topic;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.jetbrains.edu.learning.stepic.StepicUser;
import com.jetbrains.edu.learning.ui.StudyStepicUserWidget;
import org.jetbrains.annotations.Nullable;

@State(name = "StepicUpdateSettings", storages = @Storage("other.xml"))
public class StudySettings implements PersistentStateComponent<StudySettings> {
  public static final Topic<StudySettingsListener> SETTINGS_CHANGED = Topic.create("Edu.UserSet", StudySettingsListener.class);
  private StepicUser myUser;
  public long LAST_TIME_CHECKED = 0;
  private boolean myEnableTestingFromSamples = false;
  public boolean myShouldUseJavaFx = StudyUtils.hasJavaFx();

  public StudySettings() {
  }

  public long getLastTimeChecked() {
    return LAST_TIME_CHECKED;
  }

  public void setLastTimeChecked(long timeChecked) {
    LAST_TIME_CHECKED = timeChecked;
  }

  @Nullable
  @Override
  public StudySettings getState() {
    return this;
  }

  @Override
  public void loadState(StudySettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static StudySettings getInstance() {
    return ServiceManager.getService(StudySettings.class);
  }

  @Nullable
  public StepicUser getUser() {
    return myUser;
  }

  public void setUser(@Nullable final StepicUser user) {
    myUser = user;
    ApplicationManager.getApplication().getMessageBus().syncPublisher(SETTINGS_CHANGED).settingsChanged();
    updateStepicUserWidget();
  }

  public boolean shouldUseJavaFx() {
    return myShouldUseJavaFx;
  }

  public void setShouldUseJavaFx(boolean shouldUseJavaFx) {
    this.myShouldUseJavaFx = shouldUseJavaFx;
  }

  private static void updateStepicUserWidget() {
    StudyStepicUserWidget widget = StudyUtils.getStepicWidget();
    if (widget != null) {
      widget.update();
    }
  }

  public boolean isEnableTestingFromSamples() {
    return myEnableTestingFromSamples;
  }

  public void setEnableTestingFromSamples(boolean enableTestingFromSamples) {
    myEnableTestingFromSamples = enableTestingFromSamples;
    ApplicationManager.getApplication().getMessageBus().syncPublisher(SETTINGS_CHANGED).settingsChanged();
  }

  @FunctionalInterface
  public interface StudySettingsListener {
    void settingsChanged();
  }
}
