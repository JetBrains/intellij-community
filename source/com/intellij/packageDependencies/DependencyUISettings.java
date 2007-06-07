package com.intellij.packageDependencies;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;

@State(
  name = "DependencyUISettings",
  storages = {
    @Storage(
      id ="other",
      file = "$APP_CONFIG$/other.xml"
    )}
)
public class DependencyUISettings implements PersistentStateComponent<DependencyUISettings> {
  public boolean UI_FLATTEN_PACKAGES = true;
  public boolean UI_SHOW_FILES = true;
  public boolean UI_SHOW_MODULES = true;
  public boolean UI_FILTER_LEGALS = false;
  public boolean UI_GROUP_BY_SCOPE_TYPE = true;
  public boolean UI_GROUP_BY_FILES = false;
  public boolean UI_COMPACT_EMPTY_MIDDLE_PACKAGES = true;

  public static DependencyUISettings getInstance() {
    return ServiceManager.getService(DependencyUISettings.class);
  }

  public DependencyUISettings getState() {
    return this;
  }

  public void loadState(DependencyUISettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}