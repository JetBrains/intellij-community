package org.jetbrains.idea.maven.project;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.core.util.DummyProjectComponent;

/**
 * @author Vladislav.Kaznacheev
 */
@State(name = "MavenImportPreferences", storages = {@Storage(id = "default", file = "$WORKSPACE_FILE$")})
public class MavenWorkspaceSettingsComponent extends DummyProjectComponent implements PersistentStateComponent<MavenWorkspaceSettings> {

  public MavenWorkspaceSettingsComponent() {
    super("MavenImportPreferences");
  }

  public static MavenWorkspaceSettingsComponent getInstance(final Project project) {
    return project.getComponent(MavenWorkspaceSettingsComponent.class);
  }

  private MavenWorkspaceSettings mySettings = new MavenWorkspaceSettings();

  public MavenWorkspaceSettings getState() {
    return mySettings;
  }

  public void loadState(MavenWorkspaceSettings state) {
    mySettings = state;
  }
}
