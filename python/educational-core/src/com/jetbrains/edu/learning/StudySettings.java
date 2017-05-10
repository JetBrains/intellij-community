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
  public static final Topic<UserSetListener> USER_SET = Topic.create("Edu.UserSet", UserSetListener.class);
  private StepicUser myUser;
  public long LAST_TIME_CHECKED = 0;
  private boolean myEnableTestingFromSamples = false;
  private boolean isCourseCreatorEnabled = false;

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
    ApplicationManager.getApplication().getMessageBus().syncPublisher(USER_SET).userSet(user);
    updateStepicUserWidget();
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
  }

  public boolean isCourseCreatorEnabled() {
    return isCourseCreatorEnabled;
  }

  public void setCourseCreatorEnabled(boolean courseCreatorEnabled) {
    isCourseCreatorEnabled = courseCreatorEnabled;
  }

  @FunctionalInterface
  public interface UserSetListener {
    void userSet(StepicUser user);
  }
}
