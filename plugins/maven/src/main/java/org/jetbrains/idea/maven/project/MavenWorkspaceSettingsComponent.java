package org.jetbrains.idea.maven.project;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.utils.SimpleProjectComponent;

@State(name = "MavenImportPreferences", storages = {@Storage(id = "default", file = "$WORKSPACE_FILE$")})
public class MavenWorkspaceSettingsComponent extends SimpleProjectComponent implements PersistentStateComponent<MavenWorkspaceSettings> {
  private MavenWorkspaceSettings mySettings = new MavenWorkspaceSettings();

  public static MavenWorkspaceSettingsComponent getInstance(Project project) {
    return project.getComponent(MavenWorkspaceSettingsComponent.class);
  }

  public MavenWorkspaceSettingsComponent(Project project) {
    super(project);
  }

  public MavenWorkspaceSettings getState() {
    return mySettings;
  }

  public void loadState(MavenWorkspaceSettings state) {
    mySettings = state;
  }
}
