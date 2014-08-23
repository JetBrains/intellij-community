package org.jetbrains.plugins.settingsRepository;

import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.Nullable;

@State(name = "IcsProjectId", roamingType = RoamingType.DISABLED, storages = {@Storage(file = StoragePathMacros.WORKSPACE_FILE)})
public class ProjectId implements PersistentStateComponent<ProjectId> {
  public String uid;
  public String path;

  @Nullable
  @Override
  public ProjectId getState() {
    return this;
  }

  @Override
  public void loadState(ProjectId state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}