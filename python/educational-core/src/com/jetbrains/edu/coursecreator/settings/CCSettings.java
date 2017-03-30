package com.jetbrains.edu.coursecreator.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.Nullable;

@State(name = "CCSettings", storages = @Storage("other.xml"))
public class CCSettings implements PersistentStateComponent<CCSettings.State> {
  private CCSettings.State myState = new CCSettings.State();

  public static class State {
    public boolean isHtmlDefault = true;
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
  
  public boolean useHtmlAsDefaultTaskFormat() {
    return myState.isHtmlDefault;
  }
  
  public void setUseHtmlAsDefaultTaskFormat(final boolean useHtml) {
    myState.isHtmlDefault = useHtml;
  }

  public static CCSettings getInstance() {
    return ServiceManager.getService(CCSettings.class);
  }
}
