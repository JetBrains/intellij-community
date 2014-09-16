package org.jetbrains.plugins.settingsRepository;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.Nullable;

@State(name = "IcsProjectId", storages = {@Storage(file = StoragePathMacros.WORKSPACE_FILE)})
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
