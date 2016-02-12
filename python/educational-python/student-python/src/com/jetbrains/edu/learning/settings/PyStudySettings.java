package com.jetbrains.edu.learning.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("MethodMayBeStatic")
@State(name = "PyStudySettings", storages = @Storage("py_study_settings.xml"))
public class PyStudySettings implements PersistentStateComponent<PyStudySettings.State> {

  private State myState = new State();


  public static class State {
    public boolean askToTweet = true;
  }

  public static PyStudySettings getInstance(@NotNull final Project project) {
    return ServiceManager.getService(project, PyStudySettings.class);
  }
  @Nullable
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState = state;
  }
  
  public boolean askToTweet() {
    return myState.askToTweet;
  }
  
  public void setAskToTweet(final boolean askToTweet) {
    myState.askToTweet = askToTweet;
  }
}