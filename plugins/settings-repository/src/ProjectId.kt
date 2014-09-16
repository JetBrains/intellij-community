package org.jetbrains.plugins.settingsRepository;

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
